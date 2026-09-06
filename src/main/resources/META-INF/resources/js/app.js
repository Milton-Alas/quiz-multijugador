/* ============================================================
   Quiz Multijugador — cliente (JavaScript vanilla)
   El servidor es la autoridad del tiempo y de las respuestas:
   aquí solo se dibuja, se informa y se envían las intenciones.

   Dos modos:
   - ONLINE: cada quien en su dispositivo; todos responden la misma
     pregunta a la vez.
   - LOCAL:  los 3 en la misma pantalla; cada pregunta la responde un
     jugador por turnos. El servidor anuncia el turno (NEW_QUESTION con
     "player") y califica al instante al responder.
   ============================================================ */
(function () {
    'use strict';

    var RESULT_DISPLAY_MS = 3800; // tiempo que se muestra el resultado de la ronda
    var NICK_STORAGE = 'quiz.nickname';
    var PALETTE = ['#e63946', '#457b9d', '#2a9d8f', '#e9c46a', '#9b5de5'];

    var state = {
        gameId: null,
        playerId: null,       // en LOCAL es el primero de la sala (enlace del WS)
        nickname: '',
        mode: 'ONLINE',       // 'ONLINE' | 'LOCAL'
        totalRounds: 0,
        players: [],          // jugadores de esta partida (con score)
        ws: null,
        question: null,
        pendingQuestion: null,
        myScore: 0,
        roundAnswered: false,
        clientExpired: false,
        timerHandle: null,
        resultTimerHandle: null,
        resultUntil: 0
    };

    /* ---------- Utilidades DOM ---------- */
    function $(id) { return document.getElementById(id); }

    function show(screen) {
        var screens = ['home', 'create', 'join', 'lobby', 'play', 'ranking'];
        screens.forEach(function (s) {
            $('screen-' + s).classList.toggle('d-none', s !== screen);
        });
        window.scrollTo(0, 0);
    }

    function setError(screen, message) {
        var el = $('err-' + screen);
        if (!message) {
            el.classList.add('d-none');
            el.textContent = '';
            return;
        }
        el.textContent = message;
        el.classList.remove('d-none');
    }

    function escapeHtml(text) {
        return String(text).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function initials(nickname) {
        return nickname.trim().slice(0, 2).toUpperCase();
    }

    function playerIndex(playerId) {
        for (var i = 0; i < state.players.length; i++) {
            if (state.players[i].playerId === playerId) { return i; }
        }
        return 0;
    }

    function playerColor(playerId) {
        return PALETTE[playerIndex(playerId) % PALETTE.length];
    }

    function isLocal() { return state.mode === 'LOCAL'; }

    /* ---------- API REST ---------- */
    async function api(path, options) {
        var response = await fetch(path, options);
        var body = null;
        try { body = await response.json(); } catch (e) { /* sin cuerpo JSON */ }
        if (!response.ok) {
            throw new Error((body && body.message) || 'Error del servidor (' + response.status + ')');
        }
        return body;
    }

    function postJson(path, data) {
        return api(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
    }

    /* ---------- WebSocket ---------- */
    function connectSocket() {
        var protocol = location.protocol === 'https:' ? 'wss://' : 'ws://';
        state.ws = new WebSocket(protocol + location.host + '/ws/game');

        state.ws.onopen = function () {
            sendWs({ type: 'JOIN', gameId: state.gameId, playerId: state.playerId });
            refreshRoster(); // carga inicial de la sala por REST
            show('lobby');
            setError('lobby', null);
        };

        state.ws.onmessage = function (event) {
            var message = JSON.parse(event.data);
            handleServerEvent(message);
        };

        state.ws.onclose = function () {
            // Si la partida seguía abierta, avisamos
            if (state.gameId) {
                show('home');
                alert('Se perdió la conexión con el servidor. Vuelve a intentarlo.');
                resetToHome();
            }
        };
    }

    function sendWs(message) {
        if (state.ws && state.ws.readyState === WebSocket.OPEN) {
            state.ws.send(JSON.stringify(message));
        }
    }

    /* ---------- Eventos del servidor ---------- */
    function handleServerEvent(message) {
        var type = message.type;
        var payload = message.payload || {};

        switch (type) {
            case 'PLAYER_JOINED':
                state.players = payload.players || [];
                renderLobby();
                break;
            case 'PLAYER_LEFT':
                state.players = payload.players || [];
                renderLobby();
                break;
            case 'GAME_STARTED':
                state.players = payload.players || [];
                state.totalRounds = payload.totalRounds;
                show('play');
                applyModeUI();
                updateScoreHeader();
                break;
            case 'NEW_QUESTION':
                if (Date.now() < state.resultUntil) {
                    state.pendingQuestion = payload; // llega justo tras el resultado
                } else {
                    showQuestion(payload);
                }
                break;
            case 'PLAYER_ANSWERED':
                updateAnswered(payload);
                break;
            case 'QUESTION_RESULT':
                showRoundResult(payload);
                break;
            case 'GAME_FINISHED':
                showFinalRanking(payload);
                break;
            case 'ERROR':
                alert(payload.message || 'Error');
                break;
            default:
                break;
        }
    }

    /* ---------- Sala de espera ---------- */
    function renderLobby() {
        $('lobby-code').textContent = state.gameId;

        var local = isLocal();
        $('lobby-code-box').classList.toggle('d-none', local);
        $('lobby-local-note').classList.toggle('d-none', !local);
        $('lobby-players-title').textContent = local ? 'Jugadores en esta pantalla' : 'Jugadores en la sala';
        $('btn-start').textContent = local ? '🚀 ¡Comenzar!' : '🚀 ¡Comenzar!';

        var list = $('lobby-players');
        list.innerHTML = '';
        state.players.forEach(function (player, index) {
            var item = document.createElement('li');
            item.className = 'list-group-item';
            item.innerHTML =
                '<span class="player-avatar" style="background:' + playerColor(player.playerId) + '">' +
                escapeHtml(initials(player.nickname)) + '</span>' +
                '<span class="fw-bold">' + escapeHtml(player.nickname) + '</span>' +
                (local ? '<span class="turn-order ms-auto text-muted">Jugador ' + (index + 1) + '</span>'
                       : '<span class="player-dot" title="Conectado"></span>');
            list.appendChild(item);
        });
    }

    /* ---------- Juego: cabecera y turnos ---------- */
    function applyModeUI() {
        var local = isLocal();
        $('score-online').classList.toggle('d-none', local);
        $('local-scoreboard').classList.toggle('d-none', !local);
        $('turn-banner').classList.add('d-none');
        if (local) {
            renderLocalScoreboard();
        }
    }

    function updateScoreHeader() {
        if (isLocal()) {
            renderLocalScoreboard();
        } else {
            $('play-score').textContent = state.myScore;
        }
    }

    function renderLocalScoreboard() {
        var box = $('local-scoreboard');
        box.innerHTML = '';
        state.players.forEach(function (player, index) {
            var chip = document.createElement('span');
            chip.className = 'score-chip';
            if (state.question && state.question.player &&
                    state.question.player.playerId === player.playerId) {
                chip.classList.add('active-turn');
            }
            chip.innerHTML =
                '<span class="chip-dot" style="background:' + playerColor(player.playerId) + '">' +
                (index + 1) + '</span>' +
                '<span>' + escapeHtml(player.nickname) + '</span>' +
                '<span>⭐ ' + player.score + '</span>';
            box.appendChild(chip);
        });
    }

    /* ---------- Juego: pregunta ---------- */
    function showQuestion(q) {
        state.question = q;
        state.pendingQuestion = null;
        state.roundAnswered = false;
        state.clientExpired = false;

        $('quiz-area').classList.remove('d-none');
        $('result-area').classList.add('d-none');
        setError('play', null);

        // En modo LOCAL el sistema anuncia de quién es el turno
        if (isLocal() && q.player) {
            var color = playerColor(q.player.playerId);
            var banner = $('turn-banner');
            banner.classList.remove('d-none');
            banner.style.background = color;
            banner.innerHTML = '🎯 ¡Turno de <b>' + escapeHtml(q.player.nickname) + '</b>! ' +
                '<span style="font-size:0.8em;font-weight:600">Pasa el dispositivo y responde.</span>';
        } else {
            $('turn-banner').classList.add('d-none');
        }

        $('play-round').textContent = 'Ronda ' + q.round + '/' + state.totalRounds;
        $('play-category').textContent = q.category;
        $('play-question').textContent = q.question;

        var optionsBox = $('play-options');
        optionsBox.innerHTML = '';
        ['A', 'B', 'C', 'D'].forEach(function (letter) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'option-btn';
            button.dataset.option = letter;
            button.innerHTML =
                '<span class="option-letter">' + letter + '</span>' +
                '<span>' + escapeHtml(q['option' + letter]) + '</span>';
            button.addEventListener('click', function () {
                chooseOption(letter);
            });
            optionsBox.appendChild(button);
        });

        $('play-answered').textContent = '';
        startCountdown(q.timeLimitMs);
        updateScoreHeader();
    }

    function startCountdown(limitMs) {
        stopCountdown();
        var deadline = Date.now() + limitMs;
        var bar = $('timer-bar');
        var text = $('timer-text');
        state.timerHandle = setInterval(function () {
            var remaining = deadline - Date.now();
            if (remaining <= 0) {
                remaining = 0;
                stopCountdown();
                timesUp();
            }
            text.textContent = Math.ceil(remaining / 1000);
            bar.style.width = Math.max(0, (remaining / limitMs) * 100) + '%';
        }, 100);
    }

    function stopCountdown() {
        if (state.timerHandle) {
            clearInterval(state.timerHandle);
            state.timerHandle = null;
        }
    }

    function timesUp() {
        if (!state.question || state.roundAnswered) {
            return;
        }
        state.clientExpired = true;
        disableOptions();
        $('play-answered').textContent = '⏰ ¡Se acabó el tiempo! Espera el resultado…';
    }

    function disableOptions() {
        var buttons = $('play-options').querySelectorAll('.option-btn');
        buttons.forEach(function (b) { b.disabled = true; });
    }

    /* ---------- Responder ---------- */
    function chooseOption(letter) {
        if (!state.question || state.roundAnswered || state.clientExpired) {
            return;
        }

        // En LOCAL solo se puede responder en el turno anunciado
        if (isLocal() && !state.question.player) {
            return;
        }

        state.roundAnswered = true;
        disableOptions();

        // Marca visual de la opción elegida
        var chosen = $('play-options').querySelector('[data-option="' + letter + '"]');
        if (chosen) {
            chosen.classList.add('chosen-me');
        }

        if (isLocal()) {
            var turn = state.question.player;
            $('play-answered').textContent = '⏳ ' + turn.nickname + ' respondió. ¡Calificando…!';
            // En misma pantalla el servidor exige saber qué jugador responde
            sendWs({ type: 'ANSWER', option: letter, playerId: turn.playerId });
        } else {
            $('play-answered').textContent = '✅ Respondiste. ¡Espera a los demás…!';
            sendWs({ type: 'ANSWER', option: letter });
        }
    }

    function updateAnswered(payload) {
        if (!state.question || isLocal()) {
            return; // en LOCAL la calificación llega al instante (QUESTION_RESULT)
        }
        $('play-answered').textContent = '👀 ' + payload.answeredCount + ' de ' + payload.totalPlayers +
            ' jugador(es) respondieron';
    }

    /* ---------- Resultado de la ronda ---------- */
    function showRoundResult(result) {
        stopCountdown();
        $('quiz-area').classList.add('d-none');
        $('result-area').classList.remove('d-none');
        $('turn-banner').classList.add('d-none');

        var correct = result.correctOption;
        var local = isLocal();
        var row;
        if (local) {
            row = (result.answers || [])[0] || null; // el único jugador de la ronda
        } else {
            row = result.answers.find(function (a) { return a.playerId === state.playerId; }) || null;
        }

        // Refresca puntuaciones (el servidor manda el score de cada fila)
        (result.answers || []).forEach(function (a) {
            var p = state.players.find(function (x) { return x.playerId === a.playerId; });
            if (p) { p.score = a.score; }
        });
        if (row) {
            state.myScore = row.score;
        }
        updateScoreHeader();

        // Colorea las opciones (verde la correcta, roja la elegida mal)
        var buttons = $('play-options').querySelectorAll('.option-btn');
        buttons.forEach(function (b) {
            b.disabled = true;
            if (b.dataset.option === correct) {
                b.classList.add('correct');
            } else if (row && row.selectedOption === b.dataset.option) {
                b.classList.add('wrong');
            }
        });

        var title = $('result-title');
        var detail = $('result-detail');
        var question = state.question;

        if (local) {
            // La pantalla es compartida: el mensaje nombra al jugador del turno
            if (row && row.correct) {
                title.textContent = '🎉 ¡Correcto, ' + row.nickname + '!';
                title.className = 'result-title win';
                detail.textContent = '+' + row.points + ' puntos' +
                    (row.points > 100 ? ' (¡incluye bonus por rapidez!)' : '');
            } else if (row && row.selectedOption) {
                title.textContent = '😅 Incorrecto, ' + row.nickname;
                title.className = 'result-title lose';
                detail.textContent = 'La respuesta correcta era la ' + correct + ') ' +
                    question['option' + correct];
            } else if (row) {
                title.textContent = '⏰ Sin respuesta';
                title.className = 'result-title lose';
                detail.textContent = 'Le tocaba a ' + row.nickname +
                    '. La respuesta correcta era la ' + correct + ') ' + question['option' + correct];
            } else {
                title.textContent = '⏰ Sin respuesta';
                title.className = 'result-title lose';
            }
        } else if (row && row.correct) {
            title.textContent = '🎉 ¡Correcto!';
            title.className = 'result-title win';
            detail.textContent = 'Ganaste ' + row.points + ' puntos' +
                (row.points > 100 ? ' (¡incluye bonus por rapidez!)' : '');
        } else if (row && row.selectedOption) {
            title.textContent = '😅 Incorrecto';
            title.className = 'result-title lose';
            detail.textContent = 'La respuesta correcta era la ' + correct + ') ' +
                question['option' + correct];
        } else {
            title.textContent = '⏰ Sin respuesta';
            title.className = 'result-title lose';
            detail.textContent = 'La respuesta correcta era la ' + correct + ') ' +
                question['option' + correct];
        }

        state.resultUntil = Date.now() + RESULT_DISPLAY_MS;
        if (state.resultTimerHandle) {
            clearTimeout(state.resultTimerHandle);
        }
        state.resultTimerHandle = setTimeout(afterResultDisplay, RESULT_DISPLAY_MS);
    }

    function afterResultDisplay() {
        if (state.pendingQuestion) {
            showQuestion(state.pendingQuestion);
        } else {
            // Aún no llegó la siguiente pregunta: muestra la puntuación y espera
            $('quiz-area').classList.remove('d-none');
            $('result-area').classList.add('d-none');
            $('play-question').textContent = '⏳ Preparando la siguiente pregunta…';
            $('play-options').innerHTML = '';
            $('play-answered').textContent = '';
            updateScoreHeader();
        }
    }

    /* ---------- Ranking final ---------- */
    function showFinalRanking(payload) {
        stopCountdown();
        if (state.resultTimerHandle) {
            clearTimeout(state.resultTimerHandle);
        }
        show('ranking');

        var note = $('ranking-note');
        if (payload.insufficientQuestions) {
            note.textContent = payload.message || 'No había suficientes preguntas';
            note.classList.remove('d-none');
        } else {
            note.classList.add('d-none');
        }

        var list = $('ranking-list');
        list.innerHTML = '';
        payload.ranking.forEach(function (entry, index) {
            var rank = index + 1;
            var item = document.createElement('li');
            item.className = 'list-group-item';
            var isMe = !isLocal() && entry.playerId === state.playerId;
            item.innerHTML =
                '<span class="rank-badge rank-' + rank + '">' + rank + '</span>' +
                '<span class="fw-bold flex-grow-1">' + escapeHtml(entry.nickname) +
                (isMe ? ' <small>(tú)</small>' : '') + '</span>' +
                '<span class="fs-3">⭐ ' + entry.score + '</span>';
            list.appendChild(item);
        });
    }

    /* ---------- Flujo de creación / unión ---------- */
    async function refreshRoster() {
        try {
            var game = await api('/api/games/' + state.gameId);
            state.players = game.players || [];
            state.totalRounds = game.totalRounds;
            renderLobby();
        } catch (e) {
            /* la sala pudo terminar mientras tanto */
        }
    }

    function selectedCreateMode() {
        var selected = $('create-mode').querySelector('.mode-option.selected');
        return selected ? selected.dataset.mode : 'ONLINE';
    }

    function applyCreateModeUI() {
        var mode = selectedCreateMode();
        var local = mode === 'LOCAL';
        $('online-nick-block').classList.toggle('d-none', local);
        $('local-players-panel').classList.toggle('d-none', !local);
    }

    function storeNickname(nickname) {
        try { localStorage.setItem(NICK_STORAGE, nickname); } catch (e) { /* sin almacenamiento */ }
    }

    function loadNickname() {
        try { return localStorage.getItem(NICK_STORAGE) || ''; } catch (e) { return ''; }
    }

    async function createGame() {
        setError('create', null);
        var rounds = parseInt($('create-rounds').querySelector('.round-option.selected').dataset.rounds, 10);
        var mode = selectedCreateMode();
        var names = [];

        if (mode === 'ONLINE') {
            var nickname = $('create-nick').value.trim();
            if (!nickname) { setError('create', 'Escribe tu nombre para continuar.'); return; }
            names.push(nickname);
        } else {
            ['local-n1', 'local-n2', 'local-n3'].forEach(function (id) {
                var value = $(id).value.trim();
                if (value) { names.push(value); }
            });
            if (names.length < 2) {
                setError('create', 'En modo misma pantalla escribe al menos 2 jugadores.');
                return;
            }
            var lowered = names.map(function (n) { return n.toLowerCase(); });
            if (new Set(lowered).size !== names.length) {
                setError('create', 'Los jugadores deben tener nombres diferentes.');
                return;
            }
        }

        try {
            var game = await postJson('/api/games', { totalRounds: rounds, mode: mode });
            var joined = [];
            for (var i = 0; i < names.length; i++) {
                var player = await postJson('/api/games/' + game.gameId + '/players', { nickname: names[i] });
                joined.push(player);
            }
            enterGame(game, joined);
        } catch (e) {
            setError('create', e.message);
        }
    }

    async function joinGame() {
        setError('join', null);
        var nickname = $('join-nick').value.trim();
        var code = $('join-code').value.trim();
        if (!nickname) { setError('join', 'Escribe tu nombre para continuar.'); return; }
        if (code.length < 3) { setError('join', 'Escribe el código de la sala.'); return; }

        try {
            var player = await postJson('/api/games/' + code + '/players', { nickname: nickname });
            var game = await api('/api/games/' + code);
            enterGame(game, [player]);
        } catch (e) {
            setError('join', e.message);
        }
    }

    function enterGame(game, joined) {
        state.gameId = game.gameId;
        state.mode = game.mode || 'ONLINE';
        state.players = joined.slice();
        // En ONLINE cada dispositivo es un jugador; en LOCAL la pantalla actúa
        // por todos y el WS se enlaza al primero de la sala.
        state.playerId = joined[0].playerId;
        state.nickname = joined[0].nickname;
        state.myScore = 0;
        if (state.mode === 'ONLINE') {
            storeNickname(state.nickname);
        }
        connectSocket();
    }

    /* ---------- Reset ---------- */
    function resetToHome() {
        if (state.ws) { try { state.ws.close(); } catch (e) { /* noop */ } }
        stopCountdown();
        if (state.resultTimerHandle) { clearTimeout(state.resultTimerHandle); }
        state.gameId = null;
        state.playerId = null;
        state.question = null;
        state.pendingQuestion = null;
        state.players = [];
        state.mode = 'ONLINE';
        state.myScore = 0;
        ['create', 'join', 'lobby', 'play'].forEach(function (s) { setError(s, null); });

        // Vuelve el formulario de crear a su estado por defecto (modo en línea)
        $('create-mode').querySelectorAll('.mode-option').forEach(function (b) {
            b.classList.toggle('selected', b.dataset.mode === 'ONLINE');
        });
        applyCreateModeUI();
        ['create-nick', 'join-nick', 'join-code', 'local-n1', 'local-n2', 'local-n3']
            .forEach(function (id) { $(id).value = ''; });
        var stored = loadNickname();
        if (stored) {
            $('create-nick').value = stored;
            $('join-nick').value = stored;
        }
        show('home');
    }

    /* ---------- Arranque ---------- */
    function boot() {
        // Navegación entre pantallas
        $('btn-go-create').addEventListener('click', function () { show('create'); });
        $('btn-go-join').addEventListener('click', function () { show('join'); });
        document.querySelectorAll('[data-back]').forEach(function (btn) {
            btn.addEventListener('click', resetToHome);
        });

        // Selector de modalidad (crear)
        $('create-mode').addEventListener('click', function (event) {
            var target = event.target.closest('.mode-option');
            if (!target) { return; }
            $('create-mode').querySelectorAll('.mode-option').forEach(function (b) {
                b.classList.toggle('selected', b === target);
            });
            applyCreateModeUI();
        });

        // Selector de rondas (crear)
        $('create-rounds').addEventListener('click', function (event) {
            var target = event.target.closest('.round-option');
            if (!target) { return; }
            $('create-rounds').querySelectorAll('.round-option').forEach(function (b) {
                b.classList.toggle('selected', b === target);
            });
        });

        $('btn-create').addEventListener('click', createGame);
        $('create-nick').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { createGame(); }
        });
        ['local-n1', 'local-n2', 'local-n3'].forEach(function (id) {
            $(id).addEventListener('keydown', function (e) {
                if (e.key === 'Enter') { createGame(); }
            });
        });

        $('btn-join').addEventListener('click', joinGame);
        $('join-nick').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { joinGame(); }
        });
        $('join-code').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { joinGame(); }
        });

        // Comenzar partida (cualquier jugador puede hacerlo en el MVP)
        $('btn-start').addEventListener('click', function () {
            setError('lobby', null);
            if (!state.ws || state.ws.readyState !== WebSocket.OPEN) {
                setError('lobby', 'Aún no estás conectado. Espera un momento…');
                return;
            }
            $('btn-start').disabled = true;
            sendWs({ type: 'START_GAME' });
        });

        // Ranking final
        $('btn-again').addEventListener('click', resetToHome);
        $('btn-home-ranking').addEventListener('click', resetToHome);

        // Nombre recordado
        var stored = loadNickname();
        if (stored) {
            $('create-nick').value = stored;
            $('join-nick').value = stored;
        }
        applyCreateModeUI();

        show('home');
    }

    document.addEventListener('DOMContentLoaded', boot);
})();
