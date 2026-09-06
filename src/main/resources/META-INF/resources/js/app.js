/* ============================================================
   Quiz Multijugador — cliente (JavaScript vanilla)
   El servidor es la autoridad del tiempo y de las respuestas:
   aquí solo se dibuja, se informa y se envían las intenciones.
   ============================================================ */
(function () {
    'use strict';

    var RESULT_DISPLAY_MS = 3800; // tiempo que se muestra el resultado de la ronda
    var NICK_STORAGE = 'quiz.nickname';

    var state = {
        gameId: null,
        playerId: null,
        nickname: '',
        totalRounds: 0,
        players: [],
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
                updateAnswered(payload.answeredCount, payload.totalPlayers);
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
        var list = $('lobby-players');
        list.innerHTML = '';
        state.players.forEach(function (player) {
            var item = document.createElement('li');
            item.className = 'list-group-item';
            item.innerHTML =
                '<span class="player-avatar">' + escapeHtml(initials(player.nickname)) + '</span>' +
                '<span class="fw-bold">' + escapeHtml(player.nickname) + '</span>' +
                '<span class="player-dot" title="Conectado"></span>';
            list.appendChild(item);
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
        state.roundAnswered = true;
        disableOptions();

        // Marca visual de tu opción
        var chosen = $('play-options').querySelector('[data-option="' + letter + '"]');
        if (chosen) {
            chosen.classList.add('chosen-me');
        }
        $('play-answered').textContent = '✅ Respondiste. ¡Espera a los demás…!';

        sendWs({ type: 'ANSWER', option: letter });
    }

    function updateAnswered(answeredCount, totalPlayers) {
        if (!state.question) {
            return;
        }
        $('play-answered').textContent = '👀 ' + answeredCount + ' de ' + totalPlayers +
            ' jugador(es) respondieron';
    }

    /* ---------- Resultado de la ronda ---------- */
    function showRoundResult(result) {
        stopCountdown();
        $('quiz-area').classList.add('d-none');
        $('result-area').classList.remove('d-none');

        var correct = result.correctOption;
        var mine = result.answers.find(function (a) { return a.playerId === state.playerId; });
        state.myScore = mine ? mine.score : state.myScore;
        updateScoreHeader();

        // Colorea las opciones (verde la correcta, roja la que elegí mal)
        var buttons = $('play-options').querySelectorAll('.option-btn');
        buttons.forEach(function (b) {
            b.disabled = true;
            if (b.dataset.option === correct) {
                b.classList.add('correct');
            } else if (mine && mine.selectedOption === b.dataset.option) {
                b.classList.add('wrong');
            }
        });

        var title = $('result-title');
        var detail = $('result-detail');
        var question = state.question;

        if (mine && mine.correct) {
            title.textContent = '🎉 ¡Correcto!';
            title.className = 'result-title win';
            detail.textContent = 'Ganaste ' + mine.points + ' puntos' +
                (mine.points > 100 ? ' (¡incluye bonus por rapidez!)' : '');
        } else if (mine && mine.selectedOption) {
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
            item.innerHTML =
                '<span class="rank-badge rank-' + rank + '">' + rank + '</span>' +
                '<span class="fw-bold flex-grow-1">' + escapeHtml(entry.nickname) +
                (entry.playerId === state.playerId ? ' <small>(tú)</small>' : '') + '</span>' +
                '<span class="fs-3">⭐ ' + entry.score + '</span>';
            list.appendChild(item);
        });
    }

    /* ---------- Cabecera de puntuación ---------- */
    function updateScoreHeader() {
        $('play-score').textContent = state.myScore;
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

    function nicknameOf(screen) {
        var value = $(screen === 'create' ? 'create-nick' : 'join-nick').value.trim();
        return value;
    }

    function storeNickname(nickname) {
        try { localStorage.setItem(NICK_STORAGE, nickname); } catch (e) { /* sin almacenamiento */ }
    }

    function loadNickname() {
        try { return localStorage.getItem(NICK_STORAGE) || ''; } catch (e) { return ''; }
    }

    async function createGame() {
        setError('create', null);
        var nickname = nicknameOf('create');
        if (!nickname) { setError('create', 'Escribe tu nombre para continuar.'); return; }

        var rounds = parseInt($('create-rounds').querySelector('.round-option.selected').dataset.rounds, 10);

        try {
            var game = await postJson('/api/games', { totalRounds: rounds });
            var player = await postJson('/api/games/' + game.gameId + '/players', { nickname: nickname });
            enterGame(game.gameId, player, nickname);
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
            enterGame(code, player, nickname);
        } catch (e) {
            setError('join', e.message);
        }
    }

    function enterGame(gameId, player, nickname) {
        state.gameId = gameId;
        state.playerId = player.playerId;
        state.nickname = player.nickname || nickname;
        storeNickname(state.nickname);
        state.myScore = 0;
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
        state.myScore = 0;
        ['create', 'join', 'lobby', 'play'].forEach(function (s) { setError(s, null); });
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

        show('home');
    }

    document.addEventListener('DOMContentLoaded', boot);
})();
