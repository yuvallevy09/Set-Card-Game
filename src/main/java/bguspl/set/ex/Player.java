package bguspl.set.ex;

import bguspl.set.Env;
import java.util.concurrent.ArrayBlockingQueue; // imported by tomer to create a queue for player's actions

import javax.swing.Action;

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

    //added by tomer
    private ArrayBlockingQueue<Integer> keysPressed; // check what happens when inserting element beyond capacity

    private boolean penalized;

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
            //take a slot from the blocking queue
                
            // if we have 3 cards, ask the dealer whether we have a set or not.
            //needs to hold somehow all 3 cards that the player ask dealer if they are a set
            
            try{
                playerThread.wait();
            } catch (InterruptedException e) {}
            
            if(keysPressed.remainingCapacity() == 0 && !penalized) //check if player put all 3 tokens on the table
            {
                synchronized(dealer.lock) {
                    int i = 0;
                    for (int slot : keysPressed) {
                        dealer.cardsToCheck[i] = table.slotToCard[slot];
                    }
                    dealer.checkPlayer = id; 
                    dealer.lock.notify();
                    
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
                
                
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
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
        
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement - Tomer

        
        //needs to check whether this player is penalized or freezed at the moment, if so do nothing, else correspond, might belong to Dealer.
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
        synchronized(dealer.lock){
        // method is called from the dealer class once the player has completed a set
        //dealer is removing the cards from the table
        //freeze the player, probably by the dealer
        score++;
        env.ui.setFreeze(id, 1000);
        try {
            Thread.sleep(1000);
            } catch (InterruptedException e) {}
        keysPressed.clear();
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        synchronized(dealer.lock) {
            penalized = true;
            env.ui.setFreeze(id, 3000);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {}
        }    
    }

    public int score() {
        
        return score;
    }

    public ArrayBlockingQueue<Integer> getKeysPressed() {
        return keysPressed;
    }

    public int getId()
    {
        return id;
    }

}

  
