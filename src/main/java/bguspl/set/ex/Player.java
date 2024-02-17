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
    private ArrayBlockingQueue<Integer> playerActionQueue;

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
        this.table = table;
        this.id = id;
        this.human = human;
        playerActionQueue = new ArrayBlockingQueue<>(3);
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

        // if its the 3rd token of this player, Dealer should check whether its a set or not, might belong to Dealer
        //needs to check whether this player is penalize or freezed at the moment, if so do nothing, else correspond, might belong to Dealer.
        if(table.playersTokensLocations[id][slot] != -1) // if there is a token's player on this slot
        {
            table.removeToken(id, slot); //removes the token
            playerActionQueue.remove(slot); // removes the slot from the player's input queue
        }
        else if(playerActionQueue.remainingCapacity() >= 3 && table.playersTokensLocations[id][slot] == 1) // the player doesnt have a token on this slot 
        {
            table.placeToken(id, slot); // adds the player's token to the slot on the table
            enqueue(slot);
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
        // method is called from the dealer class once the player has completed a set
        // add one point to the player
        // needs to clear his input key queue
        //remove player's tokens from the table
        //dealer is removing the cards from the table
        //freeze the player, probably by the dealer
        score++;
        while (!isEmpty()) {
            dequeue(); // clears the player input queue           
        }

        


        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
    }

    public int score() {
        return score;
    }


// Queue methods-----------------------------------------------------------------------------------------------------
    public boolean enqueue(Integer slot)
    {
        return playerActionQueue.offer(slot); // Adds the action to the queue, returns false if the queue is full
    }

    public Integer dequeue()
    {
        return playerActionQueue.poll(); // Retrieves and removes the head of the queue, returns null if the queue is empty
    }
    
    public boolean isEmpty()
    {
        return playerActionQueue.isEmpty(); // Returns true if the queue is empty, false otherwise
    }
}

  
