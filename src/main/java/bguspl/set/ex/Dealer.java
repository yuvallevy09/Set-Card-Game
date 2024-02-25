package bguspl.set.ex;

import bguspl.set.Env;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    // lock
    public Object dealerLock = new Object();

    private int[] cardsToCheck; // check if to make private and use set function 
    public int checkPlayer;
    public ConcurrentLinkedQueue<Player> playersToCheckQueue = new ConcurrentLinkedQueue<>(); //Thread safe Q for the order which the dealer will check the players
    public boolean canPlay; //flag that says whether players can play or. didnt use it in player run yet!
    private long timeLeft;
    private long startTime;
    public long currentTime;
    private boolean warn;
    public Thread dealerThread;

    
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        terminate = false;
        canPlay = false;
        cardsToCheck = new int[env.config.featureSize];
        warn = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        dealerThread = Thread.currentThread();

        for(int i = 0; i < env.config.players; i++) {
            Thread playerThread = new Thread( players[i], env.config.playerNames[i]);
            playerThread.start();
        }
        
        while (!shouldFinish()) {
            Collections.shuffle(deck);  // Shuffle deck
            placeCardsOnTable();
            updateTimerDisplay(true);
            canPlay = true;
            activatePlayers();
            if (env.config.hints){
                table.hints();} // shows set hints to the output of the program
            timerLoop();
            canPlay = false;
            removeAllCardsFromTable();
        }

        announceWinners();
        if (!terminate) {
            terminate();
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        // end thread proccesses in proper fashion
        terminate = true;

        for(int i = env.config.players - 1; i >= 0; i--) {
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

/**
 * Checks cards should be removed from the table and removes them.
 */
private void removeCardsFromTable() {
    // TODO implement
    if(!playersToCheckQueue.isEmpty()){
        Player currPlayer = playersToCheckQueue.poll(); // The player that the dealer checks his set
        ArrayList<Player> kickPlayers = new ArrayList<>(); // store all players that needs to be kicked out of the playerToCheckQ
        int i = 0;
        for(int slot = 0; slot < env.config.tableSize; slot++){
 
            if(table.playersTokensLocations[currPlayer.id][slot] == 1){
                cardsToCheck[i] = table.slotToCard[slot];
                i++;
            }
        }
        if (env.util.testSet(cardsToCheck)) // if legal set
        { 
            for(int card : cardsToCheck)
            {
                int slot = table.cardToSlot[card];
                synchronized(table.lock[slot]){ //locks the cell where the dealer now performs an action
                
                    for (Player player : players){
                        int id = player.id;
                        // if other players had a shared card, remove their token and remove them from playersToCheckQueue, since set is no longer relevant
                        if(table.playersTokensLocations[id][slot] == 1){
                            table.removeToken(id, slot);
                            player.tokensLeft++;
                            if (playersToCheckQueue.contains(player)){
                                playersToCheckQueue.remove(player);
                                kickPlayers.add(player);
                                
                            }
                        } 
                    }
                    table.removeCard(slot); // remove card from table
                }
            }   
            if (env.config.hints)
                table.hints();
            currPlayer.point(); //player gets a point
            updateTimerDisplay(true);
            synchronized(currPlayer){
                currPlayer.notify();
            }
            for(Player player : kickPlayers){
                if(player.id != currPlayer.id){
                    player.freezeTime = 0;
                    synchronized(player){
                        player.notify(); // players who dont have set anymore will now continue to play
                    }
                }
            }
        } else {
            currPlayer.penalty(); //player gets penalized
            synchronized(currPlayer){
                currPlayer.notify();
            }
        }
    }    
}   

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement 
        int i = 0;
        while (!deck.isEmpty() & table.countCards() < env.config.tableSize) { 
            if (table.slotToCard[i] == null) {
                int card = deck.remove(0);
                table.placeCard(card, i);
            }
            i++;
        }
    }

/**
 * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
 */
private void sleepUntilWokenOrTimeout() {
    // TODO implement

    timeLeft = reshuffleTime - System.currentTimeMillis();
    if (playersToCheckQueue.isEmpty()) {
        if (!warn) {
            try{
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
        } else {
            try{
                Thread.sleep(10);
            } catch (InterruptedException e) {}
        }
    }
}

/**
 * Reset and/or update the countdown and the countdown display.
 */
private void updateTimerDisplay(boolean reset) {
    if (reset) {
        startTime = System.currentTimeMillis();
        timeLeft = env.config.turnTimeoutMillis;
        reshuffleTime = startTime + timeLeft;
        warn = false;
        env.ui.setCountdown(timeLeft, warn);
    } else {
        currentTime = System.currentTimeMillis();
        timeLeft = reshuffleTime - currentTime;
        warn = timeLeft <= env.config.turnTimeoutWarningMillis;
        if(timeLeft <= 0 ){
            timeLeft = 0;
        }
        env.ui.setCountdown(timeLeft, warn);

        for(Player player : players){
            if(player.isFrozen){
                long freezeDisplay = (long) Math.ceil((double) (player.unfreezeTime - currentTime) / 1000) * 1000;

                if(freezeDisplay <= 0){
                    player.isFrozen = false;
                }
                env.ui.setFreeze(player.id, freezeDisplay);
            }
        }


    }
}

/**
 * Returns all the cards from the table to the deck.
 */
private void removeAllCardsFromTable() {
    // TODO implement 
    for (Integer card : table.slotToCard) { 
        if (card != null) {
            deck.add(card); // add card back to deck 
            int slot = table.cardToSlot[card];
            synchronized(table.lock[slot]){ // dont need synchronized because of canPlay
                table.removeCard(slot); // remove card from slot on table
                for(int player = 0; player < env.config.players; player++){
                    for(int slots = 0; slots < env.config.tableSize; slots++){
                        if(table.playersTokensLocations[player][slots] == 1){
                            table.removeToken(player,slots);
                        }
                    }  
                }
            }
        }
    }
    for(Player player : players){
        player.getKeysPressed().clear(); // cleans up all the keys that were placed since we reset the table
        player.tokensLeft = env.config.featureSize;
        player.penalized = false;
        player.freezeTime = 0;
        if (player.isFrozen){
            env.ui.setFreeze(player.id, 0);
            player.isFrozen = false;
        }
    }
    playersToCheckQueue.clear();
}

/**
 * Check who is/are the winner/s and displays them.
*/
private void announceWinners() {
    // TODO implement
    int maxScore = Integer.MIN_VALUE;
    List<Integer> winners = new ArrayList<>();

    for (Player player : players) {
        int playerScore = player.score();
        if (playerScore > maxScore) {
            winners.clear();
            maxScore = playerScore;
            winners.add(player.id);
        } else if (playerScore == maxScore) {
            winners.add(player.id);
        }
    }

    int[] arrayOfWinners = winners.stream().mapToInt(Integer::intValue).toArray();
    env.ui.announceWinner(arrayOfWinners);
}

private void activatePlayers() {
    for (Player player : players) {
        synchronized(player){player.notify();}
    }
}
}
