package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue; // imported by tomer to create a queue for player's actions

// import javax.swing.Action;

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


    private ArrayBlockingQueue<Integer> keysPressed; // check what happens when inserting element beyond capacity

    private boolean penalized; //flag for whether the player got a penalty for announcing illegal set

    public static Object playerLock = new Object();
    private boolean checked;
    private long freezeTime;

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
        penalized = false;
        keysPressed = new ArrayBlockingQueue<>(3);
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

            checked = false;

            while (keysPressed.remainingCapacity() > 0 | penalized) {
                try {
                    synchronized(this) {wait();} // wake up player thread when key is pressed 
                } catch (InterruptedException e) {}
            }
            dealer.playersToCheckQueue.offer(this); 

            synchronized(playerLock) { // since only one player can be handled by the dealer at once 
                if (!dealer.playersToCheckQueue.contains(this)) { // if player was removed from checking queue
                    continue;
                }
                synchronized(dealer.dealerLock){ 
                    dealer.dealerLock.notify(); // wakes up dealer in sleepUntilWokenOrTimeout
                    try {
                        dealer.dealerLock.wait(); // current player thread returns key to monitor, enters waiting set, and allows dealerThread to enter
                    } catch (InterruptedException ignored) {}
                }
            } 
            if (!checked){
                try {
                    synchronized(this) {wait();} // woken up when checked 
                } catch (InterruptedException e) {}
            }
            try {
                Thread.sleep(freezeTime); 
            } catch (InterruptedException e) {}
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
                while(keysPressed.remainingCapacity() > 0 | penalized){
                    int x = (int)(Math.random()*12);
                    keyPressed(x);  
                }
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    // private int[] findSetOnTable(){
    //     List<Integer> cardsOnTable = new ArrayList<>();
    //     for (Integer card : table.slotToCard) {
    //         if(card != null)
    //         {
    //             cardsOnTable.add(card);
    //         }
    //     }
    //     List<int[]> set = env.util.findSets(cardsOnTable, 1);
    //     return set.get(0);
   // }


    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement - Tomer
        if(table.playersTokensLocations[id][slot] != -1) // if there is a token's player on this slot
        {
            table.removeToken(id, slot); //removes the token
            keysPressed.remove(slot); // removes the slot from the player's input queue
            if (penalized){
                penalized = false;
            }
        }
        else if(keysPressed.remainingCapacity() > 0) // the player doesnt have a token on this slot 
        {
            table.placeToken(id, slot); // adds the player's token to the slot on the table
            keysPressed.offer(slot);
            synchronized(this) {
                notify();  // wakes up playerThread which is waiting in the player instance monitor
            }
        }
    
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
        env.ui.setFreeze(id, 1000);
        keysPressed.clear();
        freezeTime = 1000;
        checked = true;
        synchronized(this) {notify();} // releases thread if not checked 
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        penalized = true;
        env.ui.setFreeze(id, 3000); // see if need to move to run()
        freezeTime = 3000;
        checked = true;
        synchronized(this) {notify();} // releases thread if not checked 
    }

    public int score() { // dealer threads activate this method
        
        return score;
    }

    public ArrayBlockingQueue<Integer> getKeysPressed() { //check in needs to be synchronized
        return keysPressed;
    }


    public void removeKeyPressed(int slot) { // removes the slot that was chosen with the key from the Q
        keysPressed.remove(slot);
    } 

    public boolean isAI(){
        return !human;
    }
    public Thread getThread(boolean human){
        if(human){
            return playerThread;
        }
        return aiThread;
    }
    public boolean isHuman(){
        return human;
    }

}