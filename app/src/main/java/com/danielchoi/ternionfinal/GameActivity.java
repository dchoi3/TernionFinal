package com.danielchoi.ternionfinal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.TransitionDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.PopupMenu;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Timer;

public class GameActivity extends AppCompatActivity implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    // Variables
    public Vibrator vb;
    public static final int activityRef = 2000;
    private int score = 0, count = 0, soundID[], cellCount;
    private GridBoard playerGrid, enemyGrid;
    private boolean playersTurn;
    private Set<Integer> soundsLoaded;
    private TransitionDrawable transition;

    private enum GAMEPHASE {INTRO_PHASE, SETUP_PHASE, PLAYER_PHASE, ENEMY_PHASE, GAMEOVER_PHASE}

    public GAMEPHASE gamephase;
    public GamePhase playerGP, enemyGP;
    View layout, shipName;
    ImageView battleButton, alertView, fireButton;
    InvasionThread invasion;
    SoundPool soundPool;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences phaseSP = getSharedPreferences("PHASE", Context.MODE_PRIVATE);
        String gamePhaseString = (phaseSP.getString("PHASE", GAMEPHASE.INTRO_PHASE.name()));

        loadSounds();
        defaultGlobalVariables();
        introPhase();
        // enterPhase(gamePhaseString);
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearData();
        SharedPreferences phaseSP = getSharedPreferences("PHASE", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = phaseSP.edit();
        editor.putString("PHASE", gamephase.name());
        editor.apply();
    }

    /**
     * This loads the all the sounds that  is going to be used for this game
     */
    private void loadSounds() {
        soundsLoaded = new HashSet<>();
        AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
        attrBuilder.setUsage(AudioAttributes.USAGE_GAME);

        final SoundPool.Builder spBuilder = new SoundPool.Builder();
        spBuilder.setAudioAttributes(attrBuilder.build());
        spBuilder.setMaxStreams(4);
        soundPool = spBuilder.build();
        soundID = new int[]{soundPool.load(this, R.raw.alarm, 1)};
    }

    /**
     * This method will be used to load up previous gamestates
     */
    private void loadSaveState() {
    }

    /**
     * This directs the game to the proper phase when returning to the game
     */
    private void enterPhase(String gamePhaseString) {
        defaultGlobalVariables();
        loadSaveState();

        switch (gamePhaseString) {
            case "INTRO_PHASE":
                gamephase = GAMEPHASE.INTRO_PHASE;
                introPhase();
                break;
            case "SETUP_PHASE":
                gamephase = GAMEPHASE.SETUP_PHASE;
                clearIntroPhase();
                setUpPhase();
                break;
            case "PLAYER_PHASE":
                gamephase = GAMEPHASE.PLAYER_PHASE;
                //Bad code below, but without it, it is causing issues
                //Need to go through and clear and set phases better
                //It gives a grid alignment issue
                //introPhase();
                //clearIntroPhase();
                //setUpPhase();
                //clearSetUpPhase();
                playerPhase();
                break;
            case "ENEMY_PHASE":
                gamephase = GAMEPHASE.ENEMY_PHASE;
                //introPhase();
                //clearIntroPhase();
                //setUpPhase();
                //clearSetUpPhase();
                enemyPhase();
                break;
            case "GAMEOVER_PHASE":
                gamephase = GAMEPHASE.GAMEOVER_PHASE;
                break;
            default:
                gamephase = GAMEPHASE.INTRO_PHASE;
                introPhase();
                break;
        }
    }

    /**
     * Loads the default variables need for each phase
     */
    private void defaultGlobalVariables() {
        alertView = (ImageView) findViewById(R.id.alertView);
        battleButton = (ImageView) findViewById(R.id.battleIB);
        fireButton = (ImageView) findViewById(R.id.fireIB);
        layout = findViewById(R.id.activity_game);
        shipName = findViewById(R.id.shipTV);
        cellCount = 8;
    }

    /**
     * This is the intro animiation phase Exit this phase by onTouch
     */
    private void introPhase() {
        gamephase = GAMEPHASE.SETUP_PHASE;
        shipName.setVisibility(View.GONE);
        battleButton.setVisibility(View.GONE);
        fireButton.setVisibility(View.GONE);

        Animation an = AnimationUtils.loadAnimation(this, R.anim.blink);
        alertView.startAnimation(an);
        invasion = new InvasionThread(this, Math.round(ScreenHeight()));
        invasion.execute();
        playSounds(0);

        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                clearIntroPhase();
                setUpPhase();
                return false;
            }
        });
    }

    /**
     * Calls clear data Hides the invasion Remove the touchlistener to the layout Stops animation.
     */
    private void clearIntroPhase() {
        clearData();
        findViewById(R.id.invasion).setVisibility(View.INVISIBLE);
    }

    /**
     * Setups the correcting padding. Transitions the background Shows battle button and shipname
     * Creates player grid
     */
    private void setUpPhase() {
        gamephase = GAMEPHASE.SETUP_PHASE;
        int i = layout.getPaddingTop();
        layout.setPadding(i, i, i, i);
        transition = (TransitionDrawable) findViewById(R.id.activity_game).getBackground();
        transition.reverseTransition(750);
        shipName.setVisibility(View.VISIBLE);//Temp reference
        battleButton.setVisibility(View.VISIBLE);
        fireButton.setVisibility(View.GONE);
        battleButton.setOnClickListener(this);
        fireButton.setOnClickListener(this);
        if (playerGrid == null) playerGrid = new GridBoard(this, R.id.playerGrid, true, cellCount);
        setDynamicButtonSize();
    }

    /**
     * Clears up setup phase to prep for Player phase
     */
    private void clearSetUpPhase() {
        battleButton.setVisibility(View.GONE);
        shipName.setVisibility(View.GONE);
        if (playerGrid != null) playerGrid.hideGrid();
    }

    /**
     * This is the player phase where we would pick the enemies coordinates to attack
     */
    private void playerPhase() {
        gamephase = GAMEPHASE.PLAYER_PHASE;
        playersTurn = true;
        fireButton.setVisibility(View.VISIBLE);
        playerGP = new GamePhase(this, playerGrid.getShipsPosition(), playerGrid.getShips());
        if (enemyGrid == null) enemyGrid = new GridBoard(this, R.id.enemyGrid, false, cellCount);
    }

    /**
     * This is the enemy phase that selects rand player coordinates to attack
     */
    private void enemyPhase() {
        gamephase = GAMEPHASE.ENEMY_PHASE;
        playersTurn = false;
        battleButton.setVisibility(View.VISIBLE);
        enemyGP = new GamePhase(this, enemyGrid.getShipsPosition(), enemyGrid.getShips());
        playerGrid.enemyAttack();
    }

    @Override
    public void onClick(View view) {
        vb.vibrate(10);

        if (view.getId() == R.id.battleIB) {
            transition.reverseTransition(750);
            battleButton.setVisibility(View.GONE);
            clearSetUpPhase();
            playerPhase();
            enemyGrid.showGrid();
            playerGrid.hideGrid();
            playerGrid.setLockGrid(true);
        } else if (view.getId() == R.id.fireIB) {
            if (enemyGrid.touchRow != -1 && enemyGrid.touchCol != -1) {
                enemyPhase();
                transition.reverseTransition(750);
                fireButton.setVisibility(View.GONE);
                enemyGrid.playerAttack(enemyGrid.touchRow, enemyGrid.touchCol);
                enemyGrid.hideGrid();
                playerGrid.showGrid();
            } else {
                Toast.makeText(this, "No Point Selected.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * This takes in the index value of the soundID to play
     */
    private void playSounds(int i) {
        final int index = i;
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    soundsLoaded.add(sampleId);
                    Log.i("SOUND", "Sound loaded = " + sampleId);
                    if (soundsLoaded.contains(soundID[index]))
                        soundPool.play(soundID[index], .1f, .1f, 0, -1, .81f);
                } else {
                    Log.i("SOUND", "Error cannot load sound status = " + status);
                }
            }
        });
    }

    /**
     * clears all threads and sounds
     */
    private void clearData() {
        if (invasion != null) {
            invasion.cancel(true);
            invasion = null;
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
            soundsLoaded.clear();
        }
        if (layout != null) layout.setOnTouchListener(null);
        if (alertView != null) {
            alertView.clearAnimation();
            alertView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * returns screen height for padding and alignment
     */
    private float ScreenHeight() {
        RelativeLayout linBoardGame = (RelativeLayout) findViewById(R.id.activity_game);
        Resources r = linBoardGame.getResources();
        DisplayMetrics d = r.getDisplayMetrics();
        return d.heightPixels;
    }

    private void setDynamicButtonSize() {

        int margin = playerGrid.getMarginSize();
        int height = (margin * 2) / 3;
        int width = margin * 2;

        ImageButton ib = (ImageButton) findViewById(R.id.battleIB);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) ib.getLayoutParams();
        lp.height = height;
        lp.width = width;
        ib.setLayoutParams(lp);

        ImageButton ib2 = (ImageButton) findViewById(R.id.fireIB);
        RelativeLayout.LayoutParams lp2 = (RelativeLayout.LayoutParams) ib.getLayoutParams();
        lp2.height = height;
        lp2.width = width;
        ib2.setLayoutParams(lp2);
    }

    /**
     * popupMenu...
     */
    public void popupMenu(View v) {
        vb.vibrate(10);
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(this);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu, popup.getMenu());
        popup.show();
    }

    /**
     * onMenuItemClick detects what menu item was clicked.
     *
     * @param item The menu item clicked.
     */
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        vb.vibrate(10);
        if (item.getItemId() == R.id.highScore) {
            gamephase = GAMEPHASE.INTRO_PHASE; //Temp to reset game. It should be in the GAMEOVER_PHASE
            if (playerGrid != null) {
                if (playerGrid.getHit()) score = score + 2000;
                Log.i("Test High Score", "-----");
                Intent scoreIntent = new Intent(getApplicationContext(), ScoreActivity.class);
                scoreIntent.putExtra("score", score);
                scoreIntent.putExtra("calling-Activity", activityRef);
                Log.i("Adding High Score", "----------");
                startActivity(scoreIntent);

            } else
                Toast.makeText(this, "This is a test phase button. \nMust be in game to work", Toast.LENGTH_SHORT).show();

        } else if (item.getItemId() == R.id.actionQuit) {
            gamephase = GAMEPHASE.INTRO_PHASE; //Temp to reset game. It should be in the GAMEOVER_PHASE
            onBackPressed();

        } else if (item.getItemId() == R.id.actionAI) {
            //To call AI attack
        }
        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}