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

    private boolean relevantSetAI;

    private ArrayBlockingQueue<Integer> keysPressed; // check what happens when inserting element beyond capacity

    private boolean penalized; //flag for whether the player got a penalty for announcing illegal set

    public static Object playerLock = new Object();

    public boolean isWaiting = true; // check if its better to announce as private
    public boolean legalSet = false; // check if its better to announce as private

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
          
                
            // if we have 3 cards, ask the dealer whether we have a set or not.
            //needs to hold somehow all 3 cards that the player ask dealer if they are a set
            
            if(keysPressed.remainingCapacity() == 0 && !penalized) //check if player put all 3 tokens on the table
            {
                dealer.playersToCheckQueue.offer(this); //insert the player inside the queue , check if needs to by before synchronized
                
                synchronized(dealer.dealerLock){
                dealer.dealerLock.notifyAll(); // releases the dealer from been blocked on sleepUntilWakenOrTimeout
                while(isWaiting){
                    try {
                        dealer.dealerLock.wait(); // insert the current thread to blocking on the dealerLock key.
                    } catch (InterruptedException e) {}
                } 
                    isWaiting = true; // reset isWaiting for the next time this player thread will enter the method, 
                    // this boolean changes inside the dealer class to false after he checks whether the set is legal.
                    if(legalSet)
                    {
                        point();
                    }
                    else
                    {
                        penalty();
                    }
                }
              

                }
            
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
                boolean setFound = false;
                while(!terminate & !setFound) {
                    int[] set = findSetOnTable();
                    int i = 0;
                    while(keysPressed.remainingCapacity() > 0 & relevantSetAI){
                        keyPressed(table.cardToSlot[set[i]]);
                        i++;
                    }
                    if (relevantSetAI){
                        setFound = true;
                    } else {
                        keysPressed.clear();
                    }
                }
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    private int[] findSetOnTable(){
        relevantSetAI = true;
        List<Integer> cardsOnTable = new ArrayList<>();
        for (Integer card : table.slotToCard) {
            if(card != null)
            {
                cardsOnTable.add(card);
            }
        }
        List<int[]> set = env.util.findSets(cardsOnTable, 1);
        return set.get(0);
    }


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
        synchronized(playerLock){  //Since score is a sharedVariable and we dont want other threads to change each other points
        //check if needs to be synchrnoized on a different key, since its a nested locks
        
        score++;
        env.ui.setFreeze(id, 1000);
        keysPressed.clear();
        try {
            Thread.sleep(1000); //player can't play for 1 second
            } catch (InterruptedException e) {}
        
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        synchronized(playerLock) { //check if needs to be synchrnoized on a different key, since its a nested locks
            penalized = true;
            env.ui.setFreeze(id, 3000);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {}
        }    
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

    public void setRelevantSetAI(boolean bool) { // removes the slot that was chosen with the key from the Q
        relevantSetAI = bool;
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
    


  
