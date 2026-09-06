-- =============================================================
-- V5: +5 preguntas por categoría con dificultad HARD
-- (25 por categoría -> 200 en total). Las de FÚTBOL siguen siendo
-- de historia del fútbol (mundiales, clubes y leyendas).
-- correct_option guarda la letra de la opción correcta (A-D).
-- =============================================================

INSERT INTO questions
    (category, question, option_a, option_b, option_c, option_d, correct_option, difficulty, active)
VALUES

-- ---------------- FÚTBOL: historia del fútbol (5 HARD) ----------------
    ('FÚTBOL', '¿Qué selección ganó el Mundial de Fútbol de 1966?',
     'Inglaterra', 'Alemania', 'Brasil', 'Italia', 'A', 'HARD', TRUE),
    ('FÚTBOL', '¿Qué club inglés es conocido como "los Red Devils"?',
     'Arsenal', 'Manchester United', 'Liverpool', 'Chelsea', 'B', 'HARD', TRUE),
    ('FÚTBOL', '¿Cuántas Copas del Mundo ha ganado la selección de Brasil?',
     '4', '3', '5', '6', 'C', 'HARD', TRUE),
    ('FÚTBOL', '¿En qué país nació el legendario futbolista Pelé?',
     'Argentina', 'Portugal', 'Uruguay', 'Brasil', 'D', 'HARD', TRUE),
    ('FÚTBOL', '¿Qué selección ganó la Eurocopa de 2016?',
     'Portugal', 'Francia', 'España', 'Alemania', 'A', 'HARD', TRUE),

-- ---------------- MATEMÁTICAS (5 HARD) ----------------
    ('MATEMÁTICAS', '¿Cuánto es 15 × 15?',
     '225', '250', '215', '300', 'A', 'HARD', TRUE),
    ('MATEMÁTICAS', 'Si x + 7 = 20, ¿cuánto vale x?',
     '14', '13', '27', '12', 'B', 'HARD', TRUE),
    ('MATEMÁTICAS', '¿Cuánto es 2 elevado a la sexta (2⁶)?',
     '32', '36', '64', '128', 'C', 'HARD', TRUE),
    ('MATEMÁTICAS', '¿Cuál es el máximo común divisor (MCD) de 12 y 18?',
     '3', '9', '12', '6', 'D', 'HARD', TRUE),
    ('MATEMÁTICAS', '¿Cuánto mide el ángulo complementario de 35°?',
     '55°', '145°', '65°', '35°', 'A', 'HARD', TRUE),

-- ---------------- CIENCIA (5 HARD) ----------------
    ('CIENCIA', '¿Qué órgano del cuerpo produce la insulina?',
     'El hígado', 'El páncreas', 'El riñón', 'El estómago', 'B', 'HARD', TRUE),
    ('CIENCIA', '¿Cuál es la unidad básica de la vida?',
     'El átomo', 'El tejido', 'La célula', 'El órgano', 'C', 'HARD', TRUE),
    ('CIENCIA', '¿Qué gas forma la mayor parte de la atmósfera terrestre?',
     'Oxígeno', 'Dióxido de carbono', 'Hidrógeno', 'Nitrógeno', 'D', 'HARD', TRUE),
    ('CIENCIA', '¿Cuál es el hueso más largo del cuerpo humano?',
     'El fémur', 'La tibia', 'El húmero', 'La pelvis', 'A', 'HARD', TRUE),
    ('CIENCIA', '¿Qué planeta es famoso por sus anillos visibles?',
     'Júpiter', 'Saturno', 'Urano', 'Neptuno', 'B', 'HARD', TRUE),

-- ---------------- HISTORIA (5 HARD) ----------------
    ('HISTORIA', '¿Quién fue el primer emperador de Roma?',
     'Julio César', 'Nerón', 'Augusto', 'Constantino', 'C', 'HARD', TRUE),
    ('HISTORIA', '¿En qué año terminó la Primera Guerra Mundial?',
     '1916', '1917', '1919', '1918', 'D', 'HARD', TRUE),
    ('HISTORIA', '¿Qué civilización construyó la ciudad de Chichén Itzá?',
     'Los mayas', 'Los aztecas', 'Los incas', 'Los olmecas', 'A', 'HARD', TRUE),
    ('HISTORIA', '¿Quién escribió "El origen de las especies"?',
     'Gregor Mendel', 'Charles Darwin', 'Louis Pasteur', 'Galileo Galilei', 'B', 'HARD', TRUE),
    ('HISTORIA', '¿Qué tratado puso fin a la Primera Guerra Mundial?',
     'El tratado de Ginebra', 'El tratado de Roma', 'El tratado de Versalles', 'El tratado de Viena', 'C', 'HARD', TRUE),

-- ---------------- CULTURA GENERAL (5 HARD) ----------------
    ('CULTURA GENERAL', '¿Cuántos años dura un lustro?',
     '3', '4', '10', '5', 'D', 'HARD', TRUE),
    ('CULTURA GENERAL', '¿Quién pintó el famoso cuadro "El grito"?',
     'Edvard Munch', 'Pablo Picasso', 'Vincent van Gogh', 'Salvador Dalí', 'A', 'HARD', TRUE),
    ('CULTURA GENERAL', '¿Cuál es la lengua oficial de Brasil?',
     'El español', 'El portugués', 'El inglés', 'El francés', 'B', 'HARD', TRUE),
    ('CULTURA GENERAL', '¿Qué significan las siglas ONU?',
     'Organización de las Naciones Unidas', 'Oficina de las Naciones Unidas', 'Organización Norteamericana Unida', 'Organización Mundial de la Salud', 'A', 'HARD', TRUE),
    ('CULTURA GENERAL', '¿En qué año se hundió el famoso barco Titanic?',
     '1920', '1905', '1898', '1912', 'D', 'HARD', TRUE),

-- ---------------- GEOGRAFÍA (5 HARD) ----------------
    ('GEOGRAFÍA', '¿Cuál es la capital de Noruega?',
     'Oslo', 'Estocolmo', 'Helsinki', 'Copenhague', 'A', 'HARD', TRUE),
    ('GEOGRAFÍA', '¿Qué país está formado por más de 7.000 islas?',
     'Indonesia', 'Filipinas', 'Japón', 'Maldivas', 'B', 'HARD', TRUE),
    ('GEOGRAFÍA', '¿Cuál es el río más largo de América del Sur?',
     'El Nilo', 'El Misisipi', 'El Amazonas', 'El Yangtsé', 'C', 'HARD', TRUE),
    ('GEOGRAFÍA', '¿En qué país se encuentra el monte Fuji?',
     'China', 'Corea del Sur', 'Tailandia', 'Japón', 'D', 'HARD', TRUE),
    ('GEOGRAFÍA', '¿Qué océano baña la costa oeste de México?',
     'El Pacífico', 'El Atlántico', 'El Índico', 'El Ártico', 'A', 'HARD', TRUE),

-- ---------------- ANIMALES (5 HARD) ----------------
    ('ANIMALES', '¿Cuál es el tiburón más grande del mundo?',
     'El tiburón blanco', 'El tiburón ballena', 'El tiburón martillo', 'El tiburón tigre', 'B', 'HARD', TRUE),
    ('ANIMALES', '¿Qué mamífero puede volar de verdad?',
     'El perro', 'El gato', 'El murciélago', 'El koala', 'C', 'HARD', TRUE),
    ('ANIMALES', '¿Cómo se llama el macho de la abeja?',
     'La obrera', 'La reina', 'La avispa', 'El zángano', 'D', 'HARD', TRUE),
    ('ANIMALES', '¿Qué animal marino tiene cuerpo gelatinoso y casi transparente?',
     'La medusa', 'El pulpo', 'El calamar', 'El cangrejo', 'A', 'HARD', TRUE),
    ('ANIMALES', '¿Qué animal puede regenerar partes de su cuerpo?',
     'El erizo de mar', 'La estrella de mar', 'El caracol', 'La almeja', 'B', 'HARD', TRUE),

-- ---------------- TECNOLOGÍA (5 HARD) ----------------
    ('TECNOLOGÍA', '¿Qué significa la sigla USB?',
     'Unidad de Sistema Básico', 'Universal Slow Bus', 'Universal Serial Bus', 'Unidad de Sincronización Binaria', 'C', 'HARD', TRUE),
    ('TECNOLOGÍA', '¿Qué empresa fabrica los procesadores "Core i9"?',
     'AMD', 'NVIDIA', 'Apple', 'Intel', 'D', 'HARD', TRUE),
    ('TECNOLOGÍA', '¿Qué empresa creó el videojuego Minecraft?',
     'Mojang', 'Electronic Arts', 'Ubisoft', 'Epic Games', 'A', 'HARD', TRUE),
    ('TECNOLOGÍA', '¿Qué protocolo hace que una página web sea segura (con candado)?',
     'FTP', 'HTTPS', 'HTTP', 'SMTP', 'B', 'HARD', TRUE),
    ('TECNOLOGÍA', '¿Cómo se llama la tarjeta que dibuja los gráficos de la computadora?',
     'CPU', 'RAM', 'GPU', 'SSD', 'C', 'HARD', TRUE);
