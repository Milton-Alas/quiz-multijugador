-- =============================================================
-- V2: una pregunta inicial por categoría (dificultad EASY)
-- correct_option guarda la letra de la opción correcta (A-D).
-- El backend NUNCA envía correct_option al navegador.
-- =============================================================

INSERT INTO questions
    (category, question, option_a, option_b, option_c, option_d, correct_option, difficulty, active)
VALUES
    ('FÚTBOL',          '¿Cuántos jugadores de campo tiene cada equipo en un partido de fútbol?',
     '9', '10', '11', '12', 'C', 'EASY', TRUE),

    ('MATEMÁTICAS',     '¿Cuánto es 7 × 8?',
     '54', '56', '64', '49', 'B', 'EASY', TRUE),

    ('CIENCIA',         '¿Cuál es el planeta más cercano al Sol?',
     'Mercurio', 'Venus', 'Tierra', 'Marte', 'A', 'EASY', TRUE),

    ('HISTORIA',        '¿En qué año llegó Cristóbal Colón a América?',
     '1492', '1500', '1453', '1519', 'A', 'EASY', TRUE),

    ('CULTURA GENERAL', '¿Cuántos lados tiene un triángulo?',
     '2', '3', '4', '5', 'B', 'EASY', TRUE),

    ('GEOGRAFÍA',       '¿Cuál es el país más grande del mundo?',
     'Rusia', 'Canadá', 'China', 'Estados Unidos', 'A', 'EASY', TRUE),

    ('ANIMALES',        '¿Qué animal es conocido como "el rey de la selva"?',
     'León', 'Tigre', 'Elefante', 'Leopardo', 'A', 'EASY', TRUE),

    ('TECNOLOGÍA',      '¿Qué significa CPU en una computadora?',
     'Unidad Central de Proceso', 'Tarjeta de Video', 'Disco Duro', 'Memoria Principal', 'A', 'EASY', TRUE);
