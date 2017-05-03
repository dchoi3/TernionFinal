package com.danielchoi.ternionfinal;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

/**
 * Created by Daniel on 4/8/2017.
 */

public class GridBoard extends Activity implements OnTouchListener {

    public Vibrator vb;
    private Context context;
    final static int maxN = 8;
    private boolean player, hit, lockGrid;
    private Ship ships[], selectedShip;
    private RelativeLayout gridContainer;
    private ArrayList<Point> occupiedCells;
    private int boardID, sizeOfCell, margin, gridID, soundID;
    private enum MotionStatus {DOWN, MOVE, UP}
    private MotionStatus status;
    private enum GamePhaseStatus {SETUP, GAMEPHASE}
    private GamePhaseStatus playerGpStatus;
    private LinearLayout linBoardGame;
    private ImageView[][] ivCell = new ImageView[maxN][maxN];
    private TextView shipTV;
    private View lastView, newView;
    private ImageView lastTarget, newTarget;
    private Vector<String> aiAttacks = new Vector<>(); // Stores A.I. attacks in Vector to track previous hits
    SoundPool soundPool;
    private Set<Integer> soundsLoaded;
    private boolean AIisAttacking;

    // Row/Col to be used for playerAttack().
    // Initialized to -1 since first cell on grid is 0,0
    int touchRow = -1;
    int touchCol = -1;

    public GridBoard(Context context, int bID, boolean player, int cellCount) {
        super();
        this.context = context;
        boardID = bID;
        this.player = player; //False will be AI, True will be player
        //maxN = cellCount; //Makes it crash for some reason
        setVariables();
        setBoard();
        createShips();
        setShips();
        loadSounds();
    }

    /**
     * Sets up global variables & on Touch Listeners
     */
    private void setVariables() {
        vb = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        linBoardGame = (LinearLayout) ((Activity) context).findViewById(boardID);
        gridContainer = (RelativeLayout) ((Activity) context).findViewById(R.id.gridContainer);
        shipTV = (TextView) ((Activity) context).findViewById(R.id.shipTV);
        linBoardGame.setOnTouchListener(this);
        sizeOfCell = Math.round(ScreenWidth() / (maxN + (1)));
        occupiedCells = new ArrayList<>();
        hit = false;
        gridID = R.drawable.grid;
        if (!player ) lockGrid = true;
    }

    /**
     * This method alignment and margin of the new grid. It creates grid's rows and columns based on
     * maxN Each grid is a View and stored in ivCell[][] Sets the default background image to a
     * grid.
     */
    public void setBoard() {
        margin = Math.round((sizeOfCell * 3) / 2);
        RelativeLayout.LayoutParams marginParam = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        marginParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        marginParam.addRule(RelativeLayout.CENTER_HORIZONTAL);
        marginParam.setMargins(0, 0, 0, (margin));
        gridContainer.setLayoutParams(marginParam);

        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(sizeOfCell * maxN, sizeOfCell);
        LinearLayout.LayoutParams lpCell = new LinearLayout.LayoutParams(sizeOfCell, sizeOfCell);
        LinearLayout linRow;

        for (int row = 0; row < maxN; row++) {
            linRow = new LinearLayout(context);
            for (int col = 0; col < maxN; col++) {
                ivCell[row][col] = new ImageView(context);
                linRow.addView(ivCell[row][col], lpCell);
            }
            linBoardGame.addView(linRow, lpRow);
        }
    }

    /**
     * This initializes all the Ship classes with a starting coordinate
     */
    private void createShips() {
        //context,size, headLocation, bodyLocations
        Point point;
        Ship scout_One, scout_Two, cruiser, carrier, motherShip;
        point = new Point(maxN - 1, 0); //row, cell
        scout_One = new Ship(1, point, maxN, "Scout One", player);
        point = new Point(maxN - 1, 1);
        scout_Two = new Ship(1, point, maxN, "Scout Two", player);
        point = new Point(maxN - 2, 2);
        cruiser = new Ship(2, point, maxN, "Cruiser", player);
        point = new Point(maxN - 2, 3);
        carrier = new Ship(4, point, maxN, "Carrier", player);
        point = new Point(maxN - 4, 5);
        motherShip = new Ship(12, point, maxN, "MotherShip", player);

        //Need an algorithm to set enemy ship locations at random without overlaps

        ships = new Ship[]{scout_One, scout_Two, cruiser, carrier, motherShip};
    }

    /**
     * This class clears the grid of all ship images Then it replaces them on the new location To
     * handle movements
     */
    private void setShips() {
        //This clears the board. To "Invalidate"
        for (int x = 0; x < maxN; x++) {
            for (int y = 0; y < maxN; y++) {
                ivCell[x][y].setBackgroundResource(gridID);
            }
        }
        for (Ship s : ships) {
            for (int i = 0; i < s.getShipSize(); i++) {
                ivCell[s.getBodyLocationPoints()[i].x][s.getBodyLocationPoints()[i].y].setBackgroundResource(s.getBodyResources()[i]);
                updateOccupiedCells(s.getBodyLocationPoints());
            }
        }
    }

    /**
     * This updates the occupiedCells ArrayList of Points To keep track of which Points are
     * occupied
     */
    private void updateOccupiedCells(Point pointsArray[]) {
        for (int x = 0; x < pointsArray.length; x++) {
            occupiedCells.add(pointsArray[x]);
        }
    }

    /**
     * This method get's the screen size from the resources
     *
     * @return the width in pixels
     */
    private float ScreenWidth() {
        RelativeLayout linBoardGame = (RelativeLayout) ((Activity) context).findViewById(R.id.gridContainer);
        Resources r = linBoardGame.getResources();
        DisplayMetrics d = r.getDisplayMetrics();
        return d.widthPixels;
    }

    /**
     * This is handling setting the ships to their appropriate cells For set up phase It calls
     * findViewHelper
     *
     * @return true
     */
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (view == linBoardGame) {
            int touchX = Math.round(motionEvent.getX());
            int touchY = Math.round(motionEvent.getY());

            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    status = MotionStatus.DOWN;
                    selectedShip = null;
                    findViewHelper(touchX, touchY);
                    if (selectedShip != null) {shipTV.setText(selectedShip.getShipName());}
                    if(lockGrid && !player){ //Means we are done with setup Phase
                        if(lastTarget != null){
                            lastTarget.setBackgroundResource(gridID);
                            newTarget.setBackgroundResource(R.drawable.target);
                        }else{
                            newTarget.setBackgroundResource(R.drawable.target);
                        }

                    }

                    break;
                case MotionEvent.ACTION_MOVE:
                    status = MotionStatus.MOVE;

                    if (!lockGrid) {
                        if (newView != null) lastView = newView;
                        findViewHelper(touchX, touchY);
                        if (selectedShip != null && newView != lastView) {
                            vb.vibrate(10);
                            playClick(soundID);
                            Log.i("Clearing and reset", "!");
                            occupiedCells.clear();
                            setShips();
                        }
                    }else if (!player){
                        if(newTarget != null) lastTarget = newTarget;

                        findViewHelper(touchX, touchY);
                        if(newTarget != null){
                            lastTarget.setBackgroundResource(gridID);
                            newTarget.setBackgroundResource(R.drawable.target);
                        }

                    }
                    break;
                case MotionEvent.ACTION_UP:
                    status = MotionStatus.UP;
                    break;
            }
        }
        return true;
    }

    /**
     * Takes the user's grid point committed once fire button is clicked. Stores the user's
     * selections in an array. Compares the user's selection to the occupied grid array. Handles
     * user hits/misses visuals accordingly.
     *
     * @param r The row's cell the player is targeting.
     * @param c the column's cell the player is targeting.
     */
    public void playerAttack(int r, int c) {
        // Get view coords player clicked.
        Log.i("player's target", "" + r + ", " + c);

        // store committed attack coordinates in array list of Points
        Point playerAttacks = new Point(r, c);

        // TODO ignore touch if player has previously committed a fire on that cell

        // Hit is set when coords are passed to checkIfOccupied via findViewHelper.
        // i.e. By the time player clicks the "Fire" button to call this method,
        // the hit boolean has already been set.
        // If hit, place the mushroom image; else miss and place crater image.
        if (getHit()) {
            ivCell[touchRow][touchCol].setImageResource(R.drawable.mushroom);
            Log.i("playerAttack()", "Hit :)");
        } else {
            ivCell[touchRow][touchCol].setImageResource(R.drawable.crater);
            Log.i("playerAttack()", "Miss :(");
        }

        // TODO transition back to enemy grid

        // Reset player's selection.
        touchRow = -1;
        touchCol = -1;
    }

    /**
     * A nested for loop that scans each cell in the row to find which view is being touched Takes
     * in the x & y coordinates that was touched If a matching view is found, It calls the check if
     * occupied method.
     *
     * @return the found view
     */
    @Nullable
    private void findViewHelper(int x, int y) {
        View searchView;
        LinearLayout searchRow;
        for (int row = 0; row < maxN; row++) {
            searchRow = (LinearLayout) linBoardGame.getChildAt(row); //Current row it is checking
            if (y > searchRow.getTop() && y < searchRow.getBottom()) {//If the Y coordinates are within the row Check which cell
                for (int col = 0; col < maxN; col++) {
                    searchView = searchRow.getChildAt(col);    //Current View of the current searchRow
                    if (x > searchView.getLeft() && x < searchView.getRight()) {//If the x coordinates are within the view, View found!
                        if (searchView == ivCell[row][col]) { //View found

                            touchCol = col;
                            touchRow = row;

                            if(!lockGrid){
                                checkIfOccupied(row, col);
                                newView = searchView;
                            }else{
                                newTarget = (ImageView) searchView;
                            }
                        }//if
                    }//if
                }//for search View
            }//if
        }//for searchRow
    }

    /**
     * This takes the row/col coordinates from findViewHelper and compares with the occupiedCell
     * ArrayList to if there is a ship part in that coordinate. ON_DOWN: If it is occupied it calls
     * the findWhichShip. ON_MOVE: It moves the ship to that location. compares the new location to
     * see if there is any ships in the way if there is it reverts back to the previous position.
     * After this method runs, it should traverse back into the ONTOUCH events.
     */
    private void checkIfOccupied(int row, int col) {
        // Update user selected coords each time a view on the grid is found.
        // To be used for playerAttack().
        // This seems to be the only place I can put this without causing app to crash.

        if (status == MotionStatus.DOWN || AIisAttacking) {

            for (int i = 0; i < occupiedCells.size(); i++) {
                if (occupiedCells.get(i).x == row && occupiedCells.get(i).y == col) {
                    Point p = new Point(row, col);
                    selectedShip = findWhichShip(p); //Touching View Updated
                    setHit(true);
                    Log.i("checkIfOccupied getHit", "" + getHit() + ", (" + row + ", " + col + ")");
                    break; // Exit loop when match found.
                }
            }
            if (selectedShip == null) {
                setHit(false);
                Log.i("checkIfOccupied getHit", "" + getHit() + ", (" + row + ", " + col + ")");
            }



        } else if (status == MotionStatus.MOVE) {//MotionStatus.MOVE
            if (selectedShip != null) {//Need to make sure none of the current ship parts will overlap another.
                int rowHolder = selectedShip.getHeadCoordinatePoint().x;
                int colHolder = selectedShip.getHeadCoordinatePoint().y;
                int tempRow, tempCol;
                selectedShip.moveShipTo(row, col);
                for (Ship s : ships) {
                    if (s != selectedShip) {

                        for (int i = 0; i < selectedShip.getShipSize(); i++) {
                            tempRow = selectedShip.getBodyLocationPoints()[i].x;
                            tempCol = selectedShip.getBodyLocationPoints()[i].y;

                            for (int j = 0; j < s.getShipSize(); j++) {
                                if (tempRow == s.getBodyLocationPoints()[j].x && tempCol == s.getBodyLocationPoints()[j].y) {
                                    selectedShip.moveShipTo(rowHolder, colHolder);
                                }
                            }//for
                        }//for
                    }
                }//for
            }
        }//Move
    }

    /**
     * A.I. Logic for Enemy Loop is continued to check if A.I. has selected the grid cell before if
     * it has not then it performs the attack and adds it to its vector of previous attacks.
     */
    public void enemyAttack() {
        Log.i("enemyAttack", "Begins");
        AIisAttacking = true;
        setHit(false);

        // Loop until A.I. selects a cell it has not chosen before.
        int counter = 0;
        int myRow = 0, myCol = 0;
        boolean selectionFound = false;
        String aiSelectedHit = "Empty";
        while (selectionFound == true || counter < aiAttacks.size()) {
            selectionFound = false;
            // Select random row and col
            Random newRow = new Random();
            myRow = newRow.nextInt(maxN);
            Random newCol = new Random();
            myCol = newCol.nextInt(maxN);
            Log.i("_-_-_-_", "aiRandomHit call " + counter);
            aiSelectedHit = myRow + ", " + myCol;

            while (counter < aiAttacks.size()) {
                // Check if grid has been selected before
                if (aiAttacks.get(counter).equals(aiSelectedHit)) {
                    selectionFound = true;
                    counter = 0;
                    break;
                }
                counter++;
            }
        }
        aiAttacks.add(aiSelectedHit);

        // TODO I think this is just for testing?
//        for (int i = 0; i < aiAttacks.size(); i++) {
//            Log.i("AI hits coordinate: ", aiAttacks.get(i));
//        }

        checkIfOccupied(myRow, myCol);

        if (getHit()) {
            ivCell[myRow][myCol].setImageResource(R.drawable.mushroom);
            Log.i("AI getHit", "" + getHit() + ", (" + myRow + ", " + myCol + ")");
        } else {
            ivCell[myRow][myCol].setImageResource(R.drawable.crater);
            Log.i("AI getHit", "" + getHit() + ", (" + myRow + ", " + myCol + ")");
        }
        AIisAttacking = false;
        Log.i("enemyAttack", "Ends");
    }

    /**
     * This is called from check if occupied on MOTION.DOWN This returns the Ship object that was
     * selected. AND updates the touchingview to be headCoordinate of the ship. This should never
     * return null at this point. But we can handle it if it does.
     */
    private Ship findWhichShip(Point p) {
        int row, col;
        for (Ship s : ships) {
            for (int i = 0; i < s.getShipSize(); i++) {
                row = s.getBodyLocationPoints()[i].x;
                col = s.getBodyLocationPoints()[i].y;
                if (row == p.x && col == p.y) {
                    //Point head = s.getHeadCoordinatePoint();
                    //touchingView = ivCell[head.x][head.y];
                    Log.i("VIEW FOUND: ", "!!!!!!!!!!!!!!!!");
                    return s;
                }
            }
        }
        return null;
    }

    private void loadSounds() {

        AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
        attrBuilder.setUsage(AudioAttributes.USAGE_GAME);

        soundsLoaded = new HashSet<>();
        final SoundPool.Builder spBuilder = new SoundPool.Builder();
        spBuilder.setAudioAttributes(attrBuilder.build());
        spBuilder.setMaxStreams(2);
        soundPool = spBuilder.build();
        soundID = soundPool.load(context, R.raw.click, 1);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    soundsLoaded.add(sampleId);
                } else {
                    Log.i("SOUND", "Error cannot load sound status = " + status);
                }
            }
        });
    }

    private void playClick(int id) {
        Log.i("Play AUDIO: ", "**********");
        if (soundsLoaded.contains(id)) soundPool.play(id, .5f, .5f, 0, 0, .2f);
    }

    public void hideGrid() {
        linBoardGame.setVisibility(View.GONE);
    }

    public void showGrid() {
        linBoardGame.setVisibility(View.VISIBLE);
    }

    public boolean getHit() {
        return hit;
    }

    public void setHit(boolean h) {
        hit = h;
    }

    public void setLockGrid(boolean lock) {
        lockGrid = lock;
    }

    public boolean getLockGrid() {
        return lockGrid;
    }

    public int getMarginSize() {
        return margin;
    }

    public Ship[] getShips() {
        return ships;
    }

    public ArrayList<Point> getShipsPosition() {
        return occupiedCells;
    }
}
