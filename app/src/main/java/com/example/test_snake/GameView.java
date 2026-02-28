package com.example.test_snake;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.graphics.Bitmap;                // NUEVO: importar Bitmap
import android.graphics.BitmapFactory;       // ya estaba presente

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import androidx.core.content.res.ResourcesCompat;

/**
 * Vista personalizada para el juego Snake. Conserva toda la lógica original de
 * la rama Test del repositorio, incluyendo enemigos, aliens, animación de quemarse,
 * transición de fondos y gestión de puntajes.
 * Se ha añadido el uso de sprites de manzana para la comida (normal y dorada).
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private Thread gameThread;
    private boolean running = false;
    private SurfaceHolder holder;
    private Paint paint;

    // Puntuación y constantes de la cuadrícula
    private int score = 0;
    private static final int GRID_WIDTH = 40;
    private static final int GRID_HEIGHT = 20;
    private static final int WIN_SCORE = 250;

    // La serpiente (lista de segmentos)
    private List<Point> snake;
    private Direction currentDirection = Direction.RIGHT;
    private Direction nextDirection = Direction.RIGHT;

    // La comida en la cuadrícula
    private Point food;

    // Control del tiempo entre frames
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 190;

    // Monedas y sistema relacionado
    private int coins = 0;
    private boolean isGolden = false;
    private Random random = new Random();
    private SharedPreferences prefs;
    private String equippedSkin;
    private DatabaseReference coinsRef;
    private String username;

    // Modo noche por defecto
    private boolean nightMode = true;

    // Variables para escalar el juego según la pantalla
    private int dynamicBlockSize;
    private int gameAreaWidth, gameAreaHeight;
    private int gameAreaOffsetX, gameAreaOffsetY;

    // Estado de partida: game over o victoria
    private boolean gameOver = false;
    private boolean gameWon = false;

    // Sistema de fondos y transición
    private int currentBackground = R.mipmap.fondolvl1;
    private int targetBackground = R.mipmap.fondo_marte;
    private boolean backgroundChanged = false;
    private boolean transitionInProgress = false;
    private float transitionProgress = 0f;
    private static final float TRANSITION_DURATION = 1.5f;
    private long transitionStartTime = 0;
    private boolean soundPlayed = false;

    // Sonidos
    private MediaPlayer transitionSound;
    private MediaPlayer winSound;
    private MediaPlayer burnSound;

    // Sistema de enemigos y aliens
    private List<EnemySnake> enemies;
    private int maxEnemies = 0;
    private boolean enemiesActive = false;

    private List<Alien> aliens;
    private int maxAliens = 0;
    private boolean aliensActive = false;

    // Animación de quemarse
    private BurnAnimation burnAnimation;
    private boolean isBurning = false;
    private long burnStartTime = 0;
    private static final long BURN_DURATION = 1000;

    // Sprites de manzana
    private Bitmap manzanaNormal;     // NUEVO: sprite de manzana normal
    private Bitmap manzanaDorada;     // NUEVO: sprite de manzana dorada

    // Constructores
    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GameView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    // Inicialización general de la vista
    private void init() {
        holder = getHolder();
        holder.addCallback(this);
        paint = new Paint();
        setFocusable(true);

        // Preferencias y configuraciones persistentes
        prefs = getContext().getSharedPreferences("SnakePrefs", Context.MODE_PRIVATE);
        coins = prefs.getInt("coins", 0);
        equippedSkin = prefs.getString("equipped_skin", "skin_default");
        username = prefs.getString("username", "Invitado");
        nightMode = prefs.getBoolean("night_mode", true);

        coinsRef = FirebaseDatabase.getInstance()
                .getReference("user_coins")
                .child(username);

        // Inicializar sonidos
        initTransitionSound();
        initBurnSound();
        initWinSound();

        // Inicializar listas de enemigos y aliens
        enemies = new ArrayList<>();
        aliens = new ArrayList<>();

        // NUEVO: cargar sprites de manzana
        manzanaNormal = BitmapFactory.decodeResource(getResources(), R.drawable.apple_pixel);
        manzanaDorada = BitmapFactory.decodeResource(getResources(), R.drawable.apple_golden_pixel);

        // Iniciar el juego
        initGame();
    }

    private void initTransitionSound() {
        try {
            transitionSound = MediaPlayer.create(getContext(), R.raw.lvlup);
            if (transitionSound != null) {
                transitionSound.setVolume(0.7f, 0.7f);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initWinSound() {
        try {
            winSound = MediaPlayer.create(getContext(), R.raw.win_sound);
            if (winSound != null) {
                winSound.setVolume(1.0f, 1.0f);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initBurnSound() {
        try {
            burnSound = MediaPlayer.create(getContext(), R.raw.explosion);
            if (burnSound != null) {
                burnSound.setVolume(1.0f, 1.0f);
                burnSound.setOnCompletionListener(mp -> {
                    // no-op
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Reiniciar todos los valores del juego
    private void initGame() {
        snake = new ArrayList<>();
        // Posición inicial de la serpiente (3 segmentos)
        snake.add(new Point(GRID_WIDTH / 2, GRID_HEIGHT / 2));
        snake.add(new Point(GRID_WIDTH / 2 - 1, GRID_HEIGHT / 2));
        snake.add(new Point(GRID_WIDTH / 2 - 2, GRID_HEIGHT / 2));

        // Generar comida
        generateFood();

        // Reiniciar enemigos y aliens
        enemies.clear();
        aliens.clear();
        maxEnemies = 0;
        maxAliens = 0;
        enemiesActive = false;
        aliensActive = false;

        // Reiniciar estados
        isBurning = false;
        burnAnimation = null;
        score = 0;
        gameOver = false;
        gameWon = false;
        currentDirection = Direction.RIGHT;
        nextDirection = Direction.RIGHT;

        // Reiniciar fondos y transición
        currentBackground = R.mipmap.fondolvl1;
        backgroundChanged = false;
        transitionInProgress = false;
        transitionProgress = 0f;
        soundPlayed = false;
    }

    // Generar nueva comida (manzana)
    private void generateFood() {
        int attempts = 0;
        while (attempts < 100) {
            int x = random.nextInt(GRID_WIDTH);
            int y = random.nextInt(GRID_HEIGHT);
            food = new Point(x, y);
            boolean collision = false;
            // Colisión con la serpiente
            for (Point segment : snake) {
                if (segment.equals(food)) {
                    collision = true;
                    break;
                }
            }
            // Colisión con enemigos
            if (!collision && enemiesActive) {
                for (EnemySnake enemy : enemies) {
                    if (enemy.collidesWith(food)) {
                        collision = true;
                        break;
                    }
                }
            }
            // Colisión con aliens
            if (!collision && aliensActive) {
                for (Alien alien : aliens) {
                    if (alien.collidesWith(food)) {
                        collision = true;
                        break;
                    }
                }
            }
            if (!collision) {
                break; // posición válida encontrada
            }
            attempts++;
        }
        // Asignar probabilidad para manzana dorada
        isGolden = random.nextFloat() < 0.2f;
    }

    // --- Métodos de dibujo y actualización del juego ---

    private void updateGame() {
        // Si hay animación de quemarse, actualizarla
        if (isBurning) {
            updateBurnAnimation();
            return;
        }

        // Actualizar dirección
        currentDirection = nextDirection;

        // Gestionar enemigos y aliens según la fase
        manageEnemiesAndAliens();

        // Mover enemigos
        for (EnemySnake enemy : enemies) {
            enemy.move();
        }
        // Mover aliens
        for (Alien alien : aliens) {
            alien.move();
        }

        // Colisión de serpiente con enemigos o aliens
        Point head = snake.get(0);
        for (EnemySnake enemy : enemies) {
            if (enemy.collidesWith(head)) {
                startBurnAnimation(head);
                return;
            }
        }
        for (Alien alien : aliens) {
            if (alien.collidesWith(head)) {
                startBurnAnimation(head);
                return;
            }
        }

        // Transición de fondo a partir de cierta puntuación
        if (!backgroundChanged && !transitionInProgress && score >= 50) {
            startBackgroundTransition();
        }
        if (transitionInProgress) {
            updateTransition();
        }

        // Calcular nueva posición de la cabeza de la serpiente
        Point newHead = new Point(head);
        switch (currentDirection) {
            case UP:
                newHead.y--;
                break;
            case DOWN:
                newHead.y++;
                break;
            case LEFT:
                newHead.x--;
                break;
            case RIGHT:
                newHead.x++;
                break;
        }

        // Verificar colisión con paredes
        if (newHead.x < 0 || newHead.x >= GRID_WIDTH || newHead.y < 0 || newHead.y >= GRID_HEIGHT) {
            gameOver = true;
            return;
        }

        // Verificar colisión consigo misma
        for (int i = 1; i < snake.size(); i++) {
            if (newHead.equals(snake.get(i))) {
                gameOver = true;
                return;
            }
        }

        // Añadir nueva cabeza a la serpiente
        snake.add(0, newHead);

        // Verificar si comió la manzana
        if (newHead.equals(food)) {
            score += 10;
            coins += isGolden ? 5 : 1;
            prefs.edit().putInt("coins", coins).apply();
            saveCoinsToFirebase();
            generateFood();
            calculateGameArea();
        } else {
            // Si no se comió la manzana, quitar la cola
            snake.remove(snake.size() - 1);
        }
    }

    private void drawGame(Canvas canvas) {
        // Color de fondo (modo noche/día)
        nightMode = prefs.getBoolean("night_mode", true);
        canvas.drawColor(nightMode ? Color.BLACK : Color.parseColor("#F5F5F5"));

        // Calcular área de juego
        if (dynamicBlockSize == 0) {
            calculateGameArea();
        }

        // Dibujar fondo con transición
        drawBackgroundWithTransition(canvas);

        // Dibujar bordes
        paint.setColor(nightMode ? Color.WHITE : Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setAlpha(255);
        Rect borderRect = new Rect(
                gameAreaOffsetX,
                gameAreaOffsetY,
                gameAreaOffsetX + gameAreaWidth,
                gameAreaOffsetY + gameAreaHeight
        );
        canvas.drawRect(borderRect, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);

        // --- DIBUJAR COMIDA (MANZANA) ---
        Rect foodRect = new Rect(
                gameAreaOffsetX + food.x * dynamicBlockSize,
                gameAreaOffsetY + food.y * dynamicBlockSize,
                gameAreaOffsetX + (food.x + 1) * dynamicBlockSize,
                gameAreaOffsetY + (food.y + 1) * dynamicBlockSize
        );
        // Seleccionar sprite según si es dorada
        Bitmap appleBitmap = isGolden ? manzanaDorada : manzanaNormal;
        // Dibujar la manzana escalada al rectángulo
        canvas.drawBitmap(appleBitmap, null, foodRect, null);

        // Dibujar aliens
        for (Alien alien : aliens) {
            alien.draw(canvas);
        }

        // Dibujar enemigos (serpientes)
        for (EnemySnake enemy : enemies) {
            for (int i = 0; i < enemy.body.size(); i++) {
                Point segment = enemy.body.get(i);
                // Color del enemigo: rojo brillante para la cabeza, rojo oscuro para el cuerpo
                if (i == 0) {
                    paint.setColor(Color.parseColor("#FF4444"));
                } else {
                    paint.setColor(Color.parseColor("#CC0000"));
                }
                Rect enemyRect = new Rect(
                        gameAreaOffsetX + segment.x * dynamicBlockSize,
                        gameAreaOffsetY + segment.y * dynamicBlockSize,
                        gameAreaOffsetX + (segment.x + 1) * dynamicBlockSize,
                        gameAreaOffsetY + (segment.y + 1) * dynamicBlockSize
                );
                canvas.drawRect(enemyRect, paint);
                // Borde amarillo para la cabeza y naranja para el cuerpo
                if (i == 0) {
                    paint.setColor(Color.YELLOW);
                } else {
                    paint.setColor(Color.parseColor("#FFA500"));
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                canvas.drawRect(enemyRect, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        // Dibujar animación de quemarse si está activa
        if (isBurning && burnAnimation != null) {
            burnAnimation.draw(canvas);
            // No dibujar la cabeza de la serpiente (está quemándose)
            for (int i = 1; i < snake.size(); i++) {
                Point segment = snake.get(i);
                drawSnakeSegment(canvas, segment, i);
            }
        } else {
            // Dibujar serpiente completa
            equippedSkin = prefs.getString("equipped_skin", "skin_default");
            for (int i = 0; i < snake.size(); i++) {
                drawSnakeSegment(canvas, snake.get(i), i);
            }
        }

        // Mensajes de game over o victoria
        if (gameOver && !isBurning) {
            drawGameOver(canvas);
        }
        if (gameWon) {
            drawGameWon(canvas);
        }

        // Dibujar info (nivel y progreso)
        drawGameInfo(canvas);
    }

    // Dibuja un segmento de la serpiente con el skin correspondiente
    private void drawSnakeSegment(Canvas canvas, Point segment, int index) {
        if ("skin_red".equals(equippedSkin)) {
            paint.setColor(index == 0 ? Color.parseColor("#FF4444") : Color.parseColor("#B71C1C"));
        } else if ("skin_blue".equals(equippedSkin)) {
            paint.setColor(index == 0 ? Color.parseColor("#448AFF") : Color.parseColor("#0D47A1"));
        } else {
            paint.setColor(index == 0 ? Color.GREEN : Color.rgb(0, 150, 0));
        }

        Rect segmentRect = new Rect(
                gameAreaOffsetX + segment.x * dynamicBlockSize,
                gameAreaOffsetY + segment.y * dynamicBlockSize,
                gameAreaOffsetX + (segment.x + 1) * dynamicBlockSize,
                gameAreaOffsetY + (segment.y + 1) * dynamicBlockSize
        );
        canvas.drawRect(segmentRect, paint);

        // Borde del segmento
        paint.setColor(nightMode ? Color.DKGRAY : Color.LTGRAY);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawRect(segmentRect, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // --- HUD: mensajes de victoria y game over, info de nivel ---
    private void drawGameWon(Canvas canvas) {
        paint.setColor(Color.GREEN);
        paint.setTextSize(60);
        paint.setStyle(Paint.Style.FILL);
        Typeface tf = ResourcesCompat.getFont(getContext(), R.font.vcr_osd_mono_1_001);
        if (tf != null) {
            paint.setTypeface(tf);
        } else {
            paint.setTypeface(Typeface.MONOSPACE);
        }
        String winText = "¡VICTORIA!";
        float textWidth = paint.measureText(winText);
        canvas.drawText(winText, (getWidth() - textWidth) / 2, getHeight() / 2 - 50, paint);

        paint.setColor(Color.YELLOW);
        paint.setTextSize(40);
        String scoreText = "Puntos: " + score + "/" + WIN_SCORE;
        float scoreWidth = paint.measureText(scoreText);
        canvas.drawText(scoreText, (getWidth() - scoreWidth) / 2, getHeight() / 2 + 20, paint);

        paint.setTextSize(30);
        String restartText = "Toca para jugar de nuevo";
        float restartWidth = paint.measureText(restartText);
        canvas.drawText(restartText, (getWidth() - restartWidth) / 2, getHeight() / 2 + 70, paint);
    }

    private void drawGameOver(Canvas canvas) {
        paint.setColor(Color.RED);
        paint.setTextSize(60);
        paint.setStyle(Paint.Style.FILL);
        Typeface tf = ResourcesCompat.getFont(getContext(), R.font.vcr_osd_mono_1_001);
        if (tf != null) {
            paint.setTypeface(tf);
        } else {
            paint.setTypeface(Typeface.MONOSPACE);
        }
        String gameOverText = "GAME OVER";
        float textWidth = paint.measureText(gameOverText);
        canvas.drawText(gameOverText, (getWidth() - textWidth) / 2, getHeight() / 2, paint);

        paint.setTextSize(30);
        String restartText = "Toca para reiniciar";
        float restartWidth = paint.measureText(restartText);
        canvas.drawText(restartText, (getWidth() - restartWidth) / 2, getHeight() / 2 + 50, paint);
    }

    private void drawGameInfo(Canvas canvas) {
        paint.setColor(nightMode ? Color.WHITE : Color.BLACK);
        paint.setTextSize(36);
        Typeface tf = ResourcesCompat.getFont(getContext(), R.font.vcr_osd_mono_1_001);
        if (tf != null) {
            paint.setTypeface(tf);
        } else {
            paint.setTypeface(Typeface.MONOSPACE);
        }

        // Texto del nivel de dificultad actual
        paint.setTextSize(20);
        String difficultyText = getDifficultyText();
        paint.setColor(Color.CYAN);
        canvas.drawText(difficultyText, 10, 40, paint);

        // Progreso hacia la victoria
        paint.setColor(Color.YELLOW);
        String progressText = score + "/" + WIN_SCORE;
        canvas.drawText(progressText, getWidth() - 80, 40, paint);
    }

    private String getDifficultyText() {
        if (score >= 210 && score <= 250) {
            return "Nivel: Aliens x" + aliens.size();
        } else if (score >= 130 && score <= 180) {
            return "Nivel: Enemigos x" + enemies.size();
        } else if (score >= 90 && score < 130) {
            return "Nivel: Enemigos x" + enemies.size();
        } else if (score >= 30 && score < 90) {
            return "Nivel: Enemigos x" + enemies.size();
        } else if (score >= 50) {
            return "Nivel: Fondo cambiado";
        } else {
            return "Nivel: Principiante";
        }
    }

    // -- Manejo de enemigos y aliens según la puntuación --
    private void manageEnemiesAndAliens() {
        // Fases de dificultad
        if (score < 30) {
            maxEnemies = 0;
            maxAliens = 0;
            enemiesActive = false;
            aliensActive = false;
        } else if (score >= 30 && score < 130) {
            enemiesActive = true;
            if (score >= 30 && score < 90) {
                maxEnemies = 1;
            } else {
                maxEnemies = 2;
            }
            // No hay aliens
            aliens.clear();
            aliensActive = false;
            maxAliens = 0;
        } else if (score >= 130 && score <= 180) {
            enemiesActive = true;
            if (score >= 130 && score < 150) {
                maxEnemies = 3;
            } else if (score >= 160 && score < 200) {
                maxEnemies = 5;
            } else if (score >= 230) {
                maxEnemies = 7;
            }
            // No hay aliens en esta fase
            aliens.clear();
            aliensActive = false;
            maxAliens = 0;
        } else if (score >= 180 && score < 200) {
            // Fase sin enemigos ni aliens
            enemies.clear();
            aliens.clear();
            enemiesActive = false;
            aliensActive = false;
            maxEnemies = 0;
            maxAliens = 0;
        } else if (score >= 220 && score <= 250) {
            // Fase de aliens
            aliensActive = true;
            if (score >= 220 && score < 250) {
                maxAliens = 3;
            }
            // No hay enemigos
            enemies.clear();
            enemiesActive = false;
            maxEnemies = 0;
        } else if (score >= 351 && score < WIN_SCORE) {
            // Otra fase sin enemigos ni aliens
            enemies.clear();
            aliens.clear();
            enemiesActive = false;
            aliensActive = false;
            maxEnemies = 0;
            maxAliens = 0;
        }

        // Victoria
        if (score >= WIN_SCORE && !gameWon) {
            gameWon = true;
            playWinSound();
            enemies.clear();
            aliens.clear();
        }

        // Generar enemigos y aliens según corresponda
        if (enemiesActive) {
            while (enemies.size() < maxEnemies) {
                generateEnemy();
                if (enemies.size() >= maxEnemies) break;
            }
        }
        if (aliensActive) {
            while (aliens.size() < maxAliens) {
                generateAlien();
                if (aliens.size() >= maxAliens) break;
            }
        }
    }

    // Reproducir sonido de victoria
    private void playWinSound() {
        if (transitionSound != null && transitionSound.isPlaying()) {
            transitionSound.stop();
            transitionSound.seekTo(0);
        }
        if (winSound != null) {
            try {
                winSound.seekTo(0);
                winSound.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Animación de quemarse (explosión)
    private void startBurnAnimation(Point position) {
        isBurning = true;
        burnStartTime = System.currentTimeMillis();
        burnAnimation = new BurnAnimation(position);

        // Reproducir sonido de explosión
        if (burnSound != null) {
            try {
                if (burnSound.isPlaying()) {
                    burnSound.stop();
                }
                burnSound.seekTo(0);
                burnSound.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateBurnAnimation() {
        if (!isBurning || burnAnimation == null) return;
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - burnStartTime) / 1000f;
        burnAnimation.update(deltaTime);
        if (burnAnimation.isFinished()) {
            isBurning = false;
            burnAnimation = null;
            gameOver = true;
        }
    }

    // Clases internas: Alien, EnemySnake, BurnAnimation, Particle
    private class Alien {
        Point position;
        int speed;
        Bitmap alienBitmap;

        Alien(Point startPos, int speed) {
            this.position = startPos;
            this.speed = speed;
            try {
                alienBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.alien);
                if (alienBitmap != null) {
                    int newWidth = dynamicBlockSize;
                    int newHeight = dynamicBlockSize;
                    alienBitmap = Bitmap.createScaledBitmap(alienBitmap, newWidth, newHeight, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
                alienBitmap = null;
            }
        }

        void move() {
            // Los aliens solo caen hacia abajo
            position.y += speed;
            if (position.y >= GRID_HEIGHT) {
                position.y = 0;
                position.x = random.nextInt(GRID_WIDTH);
            }
        }

        boolean collidesWith(Point point) {
            return position.equals(point);
        }

        void draw(Canvas canvas) {
            if (alienBitmap != null) {
                Rect alienRect = new Rect(
                        gameAreaOffsetX + position.x * dynamicBlockSize,
                        gameAreaOffsetY + position.y * dynamicBlockSize,
                        gameAreaOffsetX + (position.x + 1) * dynamicBlockSize,
                        gameAreaOffsetY + (position.y + 1) * dynamicBlockSize
                );
                canvas.drawBitmap(alienBitmap, null, alienRect, paint);
            } else {
                paint.setColor(Color.GREEN);
                Rect alienRect = new Rect(
                        gameAreaOffsetX + position.x * dynamicBlockSize,
                        gameAreaOffsetY + position.y * dynamicBlockSize,
                        gameAreaOffsetX + (position.x + 1) * dynamicBlockSize,
                        gameAreaOffsetY + (position.y + 1) * dynamicBlockSize
                );
                canvas.drawRect(alienRect, paint);
            }
        }
    }

    private class EnemySnake {
        List<Point> body;
        Direction direction;
        boolean isVertical;
        int speedCounter = 0;
        int speedDelay = 3; // más lento que el jugador
        int length = 3;     // longitud inicial

        EnemySnake(Point startPos, boolean vertical) {
            this.body = new ArrayList<>();
            this.isVertical = vertical;

            if (vertical) {
                this.direction = random.nextBoolean() ? Direction.DOWN : Direction.UP;
                for (int i = 0; i < length; i++) {
                    if (direction == Direction.DOWN) {
                        body.add(new Point(startPos.x, startPos.y - i));
                    } else {
                        body.add(new Point(startPos.x, startPos.y + i));
                    }
                }
            } else {
                this.direction = random.nextBoolean() ? Direction.RIGHT : Direction.LEFT;
                for (int i = 0; i < length; i++) {
                    if (direction == Direction.RIGHT) {
                        body.add(new Point(startPos.x - i, startPos.y));
                    } else {
                        body.add(new Point(startPos.x + i, startPos.y));
                    }
                }
            }
        }

        void move() {
            speedCounter++;
            if (speedCounter < speedDelay) return;
            speedCounter = 0;

            Point head = new Point(body.get(0));
            switch (direction) {
                case UP:
                    head.y--;
                    if (head.y < 0) {
                        head.y = GRID_HEIGHT - 1;
                        if (random.nextBoolean()) {
                            direction = Direction.DOWN;
                        } else if (random.nextBoolean() && !isVertical) {
                            direction = random.nextBoolean() ? Direction.LEFT : Direction.RIGHT;
                            isVertical = false;
                        }
                    }
                    break;
                case DOWN:
                    head.y++;
                    if (head.y >= GRID_HEIGHT) {
                        head.y = 0;
                        if (random.nextBoolean()) {
                            direction = Direction.UP;
                        } else if (random.nextBoolean() && !isVertical) {
                            direction = random.nextBoolean() ? Direction.LEFT : Direction.RIGHT;
                            isVertical = false;
                        }
                    }
                    break;
                case LEFT:
                    head.x--;
                    if (head.x < 0) {
                        head.x = GRID_WIDTH - 1;
                        if (random.nextBoolean()) {
                            direction = Direction.RIGHT;
                        } else if (random.nextBoolean() && isVertical) {
                            direction = random.nextBoolean() ? Direction.UP : Direction.DOWN;
                            isVertical = true;
                        }
                    }
                    break;
                case RIGHT:
                    head.x++;
                    if (head.x >= GRID_WIDTH) {
                        head.x = 0;
                        if (random.nextBoolean()) {
                            direction = Direction.LEFT;
                        } else if (random.nextBoolean() && isVertical) {
                            direction = random.nextBoolean() ? Direction.UP : Direction.DOWN;
                            isVertical = true;
                        }
                    }
                    break;
            }

            body.add(0, head);
            if (body.size() > length) {
                body.remove(body.size() - 1);
            }
        }

        boolean collidesWith(Point point) {
            for (Point segment : body) {
                if (segment.equals(point)) {
                    return true;
                }
            }
            return false;
        }

        boolean collidesWithEnemy(EnemySnake other) {
            for (Point segment : body) {
                if (other.collidesWith(segment)) {
                    return true;
                }
            }
            return false;
        }
    }

    private class BurnAnimation {
        Point position;
        float progress = 0f;
        int particleCount = 25;
        List<Particle> particles = new ArrayList<>();

        BurnAnimation(Point pos) {
            this.position = pos;
            createParticles();
        }

        void createParticles() {
            particles.clear();
            for (int i = 0; i < particleCount; i++) {
                particles.add(new Particle(
                        position.x + 0.5f,
                        position.y + 0.5f,
                        random.nextFloat() * 360,
                        random.nextFloat() * 3 + 1,
                        random.nextFloat() * 0.7f + 0.3f
                ));
            }
        }

        void update(float deltaTime) {
            progress += deltaTime / (BURN_DURATION / 1000f);
            if (progress > 1f) progress = 1f;
            for (Particle particle : particles) {
                particle.update(deltaTime);
            }
        }

        boolean isFinished() {
            return progress >= 1f;
        }

        void draw(Canvas canvas) {
            for (Particle particle : particles) {
                particle.draw(canvas);
            }
            float explosionProgress = Math.min(progress * 2, 1f);
            if (explosionProgress < 1f) {
                float explosionSize = explosionProgress * dynamicBlockSize * 1.5f;
                paint.setColor(Color.YELLOW);
                paint.setAlpha((int)(255 * (1 - explosionProgress)));
                canvas.drawCircle(
                        gameAreaOffsetX + (position.x + 0.5f) * dynamicBlockSize,
                        gameAreaOffsetY + (position.y + 0.5f) * dynamicBlockSize,
                        explosionSize / 2,
                        paint
                );
                paint.setAlpha(255);
            }
        }
    }

    private class Particle {
        float x, y;
        float angle;
        float speed;
        float life;
        float originalLife;

        Particle(float startX, float startY, float angle, float speed, float life) {
            this.x = startX;
            this.y = startY;
            this.angle = angle;
            this.speed = speed;
            this.life = life;
            this.originalLife = life;
        }

        void update(float deltaTime) {
            life -= deltaTime;
            if (life <= 0) return;
            float rad = (float) Math.toRadians(angle);
            x += Math.cos(rad) * speed * deltaTime * 8;
            y += Math.sin(rad) * speed * deltaTime * 8;
            // efecto de gravedad
            y += deltaTime * 2;
        }

        void draw(Canvas canvas) {
            if (life <= 0) return;
            float progress = life / originalLife;
            int alpha = (int)(255 * progress);
            int size = (int)(dynamicBlockSize * 0.4f * progress);
            int r = 255;
            int g = (int)(80 + 175 * (1 - progress));
            int b = 0;
            paint.setColor(Color.argb(alpha, r, g, b));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(
                    gameAreaOffsetX + x * dynamicBlockSize,
                    gameAreaOffsetY + y * dynamicBlockSize,
                    size / 2,
                    paint
            );
        }
    }

    // Enum de direcciones
    private enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    // --- Métodos relacionados con transición de fondo ---

    private void startBackgroundTransition() {
        transitionInProgress = true;
        transitionStartTime = System.currentTimeMillis();
        transitionProgress = 0f;
        playTransitionSound();
    }

    private void playTransitionSound() {
        if (transitionSound != null && !soundPlayed) {
            try {
                transitionSound.seekTo(0);
                transitionSound.start();
                soundPlayed = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateTransition() {
        long currentTime = System.currentTimeMillis();
        float elapsed = (currentTime - transitionStartTime) / 1000f;
        transitionProgress = elapsed / TRANSITION_DURATION;
        if (transitionProgress >= 1f) {
            transitionInProgress = false;
            backgroundChanged = true;
            currentBackground = targetBackground;
        }
    }

    // Generar enemigos y aliens
    private void generateAlien() {
        int startX = random.nextInt(GRID_WIDTH);
        Point startPos = new Point(startX, 0);
        int speed = random.nextInt(2) + 1;
        Alien alien = new Alien(startPos, speed);
        aliens.add(alien);
        Log.d("GameView", "Alien generado en (" + startX + ", 0). Total aliens: " + aliens.size());
    }

    private void generateEnemy() {
        int attempts = 0;
        while (attempts < 50) {
            int startX, startY;
            boolean vertical = random.nextBoolean();
            if (vertical) {
                startX = random.nextInt(GRID_WIDTH);
                startY = random.nextBoolean() ? 0 : GRID_HEIGHT - 1;
            } else {
                startY = random.nextInt(GRID_HEIGHT);
                startX = random.nextBoolean() ? 0 : GRID_WIDTH - 1;
            }
            Point startPos = new Point(startX, startY);
            EnemySnake newEnemy = new EnemySnake(startPos, vertical);

            boolean collision = false;
            for (Point segment : snake) {
                if (newEnemy.collidesWith(segment)) {
                    collision = true;
                    break;
                }
            }
            if (newEnemy.collidesWith(food)) {
                collision = true;
            }
            for (Alien alien : aliens) {
                if (alien.collidesWith(startPos)) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                for (EnemySnake existingEnemy : enemies) {
                    if (newEnemy.collidesWithEnemy(existingEnemy)) {
                        collision = true;
                        break;
                    }
                }
            }
            if (!collision) {
                enemies.add(newEnemy);
                Log.d("GameView", "Enemigo generado. Total: " + enemies.size());
                return;
            }
            attempts++;
        }
    }

    private void drawBackgroundWithTransition(Canvas canvas) {
        Rect destRect = new Rect(
                gameAreaOffsetX,
                gameAreaOffsetY,
                gameAreaOffsetX + gameAreaWidth,
                gameAreaOffsetY + gameAreaHeight
        );
        if (transitionInProgress) {
            int offset = (int)(gameAreaHeight * transitionProgress);
            Bitmap currentBg = BitmapFactory.decodeResource(getResources(), R.mipmap.fondolvl1);
            if (currentBg != null) {
                Rect currentRect = new Rect(
                        gameAreaOffsetX,
                        gameAreaOffsetY - offset,
                        gameAreaOffsetX + gameAreaWidth,
                        gameAreaOffsetY + gameAreaHeight - offset
                );
                canvas.drawBitmap(currentBg, null, currentRect, paint);
            }
            Bitmap targetBg = BitmapFactory.decodeResource(getResources(), R.mipmap.fondo_marte);
            if (targetBg != null) {
                Rect targetRect = new Rect(
                        gameAreaOffsetX,
                        gameAreaOffsetY + (gameAreaHeight - offset),
                        gameAreaOffsetX + gameAreaWidth,
                        gameAreaOffsetY + gameAreaHeight + (gameAreaHeight - offset)
                );
                canvas.drawBitmap(targetBg, null, targetRect, paint);
            }
        } else {
            Bitmap backgroundBitmap = BitmapFactory.decodeResource(getResources(), currentBackground);
            if (backgroundBitmap != null) {
                Paint backgroundPaint = new Paint();
                backgroundPaint.setAlpha(100);
                canvas.drawBitmap(backgroundBitmap, null, destRect, backgroundPaint);
            } else {
                canvas.drawColor(Color.BLACK);
            }
        }
    }

    // --- Métodos de sincronización de monedas y control de hilo ---

    private void saveCoinsToFirebase() {
        if (coinsRef != null) {
            coinsRef.setValue(coins);
        }
    }

    public void updateCoinsFromFirebase(int firebaseCoins) {
        this.coins = firebaseCoins;
        prefs.edit().putInt("coins", firebaseCoins).apply();
    }

    public void restartGame() {
        initGame();
        if (transitionSound != null && !transitionSound.isPlaying()) {
            try {
                transitionSound.seekTo(0);
                transitionSound.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void pauseBackgroundMusic() {
        if (transitionSound != null && transitionSound.isPlaying()) {
            transitionSound.pause();
        }
    }

    public void resumeBackgroundMusic() {
        if (transitionSound != null && !transitionSound.isPlaying() && !gameWon) {
            transitionSound.start();
        }
    }

    // Métodos para actualizar dirección de la serpiente
    public void setDirectionUp() {
        if (currentDirection != Direction.DOWN && !gameOver && !isBurning && !gameWon) {
            nextDirection = Direction.UP;
        }
    }

    public void setDirectionDown() {
        if (currentDirection != Direction.UP && !gameOver && !isBurning && !gameWon) {
            nextDirection = Direction.DOWN;
        }
    }

    public void setDirectionLeft() {
        if (currentDirection != Direction.RIGHT && !gameOver && !isBurning && !gameWon) {
            nextDirection = Direction.LEFT;
        }
    }

    public void setDirectionRight() {
        if (currentDirection != Direction.LEFT && !gameOver && !isBurning && !gameWon) {
            nextDirection = Direction.RIGHT;
        }
    }

    public int getScore() {
        return score;
    }

    public boolean isGameOver() {
        return gameOver || gameWon;
    }

    public boolean isGameWon() {
        return gameWon;
    }

    public void stopGame() {
        running = false;
        if (transitionSound != null) {
            transitionSound.release();
            transitionSound = null;
        }
        if (burnSound != null) {
            burnSound.release();
            burnSound = null;
        }
        if (winSound != null) {
            winSound.release();
            winSound = null;
        }
    }

    // SurfaceHolder.Callback
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (transitionSound != null) {
            transitionSound.release();
            transitionSound = null;
        }
        if (burnSound != null) {
            burnSound.release();
            burnSound = null;
        }
        if (winSound != null) {
            winSound.release();
            winSound = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        calculateGameArea();
    }

    // Hilo principal de juego
    @Override
    public void run() {
        while (running) {
            if (!holder.getSurface().isValid()) continue;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
                if (!gameOver && !gameWon) {
                    updateGame();
                }
                lastUpdateTime = currentTime;
            }

            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                drawGame(canvas);
                holder.unlockCanvasAndPost(canvas);
            }

            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Cálculo del área de juego según dimensiones de pantalla
    private void calculateGameArea() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;
        int controlsAreaWidth = (int)(100 * density);
        int margin = (int)(2 * density);
        int totalAvailableWidth = getWidth() - controlsAreaWidth;
        int gameWidth = totalAvailableWidth * 2;
        int gameHeight = (int)(getHeight() * 0.90);

        dynamicBlockSize = Math.min(gameWidth / GRID_WIDTH, gameHeight / GRID_HEIGHT);
        if (dynamicBlockSize < 40) {
            dynamicBlockSize = 40;
        }

        gameAreaWidth = GRID_WIDTH * dynamicBlockSize;
        gameAreaHeight = GRID_HEIGHT * dynamicBlockSize;
        gameAreaOffsetX = margin;
        gameAreaOffsetY = (getHeight() - gameAreaHeight) / 2;
    }
}
