package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Random;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

/**
 * The game environment object.
 */
private final Env env;



/**
 * Game entities.
 */

private final Dealer dealer;

private final Table table;

/**
 * The id of the player (starting from 0).
 */
public final int id;

/**
 * The thread representing the current player.
 */
private Thread playerThread;

/**
 * The thread of the AI (computer) player (an additional thread used to generate key presses).
 */
private Thread aiThread;

/**
 * True iff the player is human (not a computer player).
 */
private final boolean human;

/**
 * True iff game should be terminated.
 */
private volatile boolean terminate;

/**
 * The current score of the player.
 */
private int score;

//More Fields
private ArrayBlockingQueue<Integer> keysPressed; 
public long freezeTime;
public int tokensLeft;
public boolean penalized;
public long unfreezeTime;
public boolean isFrozen;

/**
 * The class constructor.
 *
 * @param env    - the environment object.
 * @param dealer - the dealer object.
 * @param table  - the table object.
 * @param id     - the id of the player.
 * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
 */

public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
    this.env = env;
    this.dealer = dealer;
    this.table = table;
    this.id = id;
    this.human = human;
    this.score = 0;
    tokensLeft = env.config.featureSize;
    freezeTime = 0;
    penalized = false;
    isFrozen = false;
    keysPressed = new ArrayBlockingQueue<>(env.config.featureSize);
}

/**
 * The main player thread of each player starts here (main loop for the player thread).
 */
@Override
public void run() {
    playerThread = Thread.currentThread();
    env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
    if (!human) createArtificialIntelligence();

    while (!terminate) {
        // TODO implement main player loop

        while (tokensLeft > 0 | penalized) {
            if (!dealer.canPlay) {
                synchronized(this){
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        if (terminate) {break;}
                    }
                }
            }
            if (!keysPressed.isEmpty()) { // see if better to wait until key is pressed
                int slot = -1; 
                try {
                    slot = keysPressed.take();
                } catch (InterruptedException e){}
                synchronized(table.lock[slot]){
                    if(table.playersTokensLocations[id][slot] == 1) // if player has token on this slot
                    {
                        table.removeToken(id, slot); //removes the token
                        tokensLeft++;
                        if (penalized) {
                            penalized = false;
                        }
                    } else if (!penalized & table.slotToCard[slot] != null) {
                        table.placeToken(id, slot); // adds the player's token to the slot on the table
                        tokensLeft--;
                    } 
                }
            }   
        }
        dealer.playersToCheckQueue.offer(this);
        
             
        synchronized(this){
            try {
                dealer.dealerThread.interrupt();
                if (!terminate) {wait();}
            } catch (InterruptedException e) {
                if (terminate) {break;}
            }
        }

        try {
            Thread.sleep(freezeTime);
        } catch (InterruptedException e) {}

        keysPressed.clear(); //Clears player keyPressQ
    }
    if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
    env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
}

/**
 * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
 * key presses. If the queue of key presses is full, the thread waits until it is not full.
 */
private void createArtificialIntelligence() {
    // note: this is a very, very smart AI (!)
    aiThread = new Thread(() -> {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        
        while (!terminate) {
            // TODO implement player key press simulator
            
            Random rand = new Random();
            int randomSlot = rand.nextInt(env.config.tableSize);
            keyPressed(randomSlot);
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }, "computer-" + id);
    aiThread.start();
}

/**
 * Called when the game should be terminated.
 */
public void terminate() {
    // TODO implement
    terminate = true;
    if(playerThread != null){
        playerThread.interrupt();
        try {
            playerThread.join();
        } catch (InterruptedException e) {}
    }
}

/**
 * This method is called when a key is pressed.
 *
 * @param slot - the slot corresponding to the key pressed.
 */
public void keyPressed(int slot) {
    // TODO implement - Tomer
    keysPressed.offer(slot);
}

/**
 * Award a point to a player and perform other related actions.
 *
 * @post - the player's score is increased by 1.
 * @post - the player's score is updated in the ui.
 */
public void point() {
    // TODO implement
    score++;
    freezeTime = env.config.pointFreezeMillis;
    unfreezeTime = freezeTime + dealer.currentTime; 
    env.ui.setFreeze(id, freezeTime);
    isFrozen = true;
    int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    env.ui.setScore(id,score);
}

/**
 * Penalize a player and perform other related actions.
 */
public void penalty() {
    // TODO implement
    freezeTime = env.config.penaltyFreezeMillis;
    unfreezeTime = freezeTime + dealer.currentTime;
    env.ui.setFreeze(id, freezeTime);
    isFrozen = true;
    penalized = true;
}

public int score() {
    return score;
 }

public ArrayBlockingQueue<Integer> getKeysPressed() {
    return keysPressed;
}

}