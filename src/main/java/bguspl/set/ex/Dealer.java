package bguspl.set.ex;

import bguspl.set.Env;
import java.util.*;
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

    private int[] cardsToCheck = new int[3]; // check if to make private and use set function 
    public int checkPlayer;
    public Queue<Player> playersToCheckQueue = new LinkedList<>(); //Queue for the order which the dealer will check the players
    //public Thread dealerThread;

  


    
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        terminate = false;
        //check if needs to update reshuffle time here or in the run method
    }

    // public void startPlayersThreads(Player[] players){
    //     for(Player player : players){
    //         player.playerThread = new Thread(player.getThread(player.isHuman()))
    //     }
    // }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        Collections.shuffle(deck);  // Shuffle deck
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        // needs to initialize player threads here according to flow chart
        
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
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
        synchronized(dealerLock) {
            Player currPlayer = playersToCheckQueue.poll(); //Dealer checks player set right now, thus needs to remove player from waiting list
            int i = 0;
            for (int slot : currPlayer.getKeysPressed()) {
                cardsToCheck[i] = table.slotToCard[slot];
                i++;
            }
            if (env.util.testSet(cardsToCheck)) // if legal set
            { 
                currPlayer.legalSet = true; // announce the player that he build a legal set
                for(int card : cardsToCheck)
                {
                    table.removeCard(table.cardToSlot[card]); // remove card from table
                    for (Player player : players){
                        // if other players had a shared card, remove their token and remove them from playersToCheckQueue, since set is no longer relevant
                        if (player.getKeysPressed().contains(table.cardToSlot[card])) { 
                            table.removeToken(player.id, table.cardToSlot[card]);
                            player.removeKeyPressed(table.cardToSlot[card]);
                            if (playersToCheckQueue.contains(player)){
                                playersToCheckQueue.remove(player);
                            }
                            if (player.isAI()){
                                player.setRelevantSetAI(false);
                               // player.aiThread.notify(); // check
                            }
                        }
                    }
                }
                dealerLock.notifyAll();
                currPlayer.isWaiting = false; // now the player will exit his loop and move on with the run method
            //     // currPlayer.point(); //player gets a point ------ moved the method into Player beacuse we want player thread to run the method
            // }
            // else 
            // {
            //     //currPlayer.penalty(); //player gets penalized ------ moved the method into Player beacuse we want player thread to run the method
            // } 
            }
        }            
    }

    //         for (Player player : players) {
    //             if (player.getKeysPressed().remainingCapacity() == 0) { // if queue is full
    //                 int[] cards = keysToCards(player.getKeysPressed()); // transfer cards selected by player to array
                    
    //         }
    //     }
    // }

    // private int[] keysToCards (ArrayBlockingQueue<Integer> keysPressed) { 
    //     int[] setOfCards = new int[3];
    //     int i = 0;
    //     for(Integer slot : keysPressed)
    //     {
    //         setOfCards[i] = table.slotToCard[slot];
    //         i++; 
    //     }
    //     return setOfCards;
    // }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement - yuval 
        int i = 0;
        while (!deck.isEmpty() & table.countCards() < 12) { 
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
       
        synchronized (dealerLock) { // synchronized on the dealerLock, then puts him to wait on this lock until he gets notified 
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            if(timeLeft > 0){
            try {
                dealerLock.wait(timeLeft);
            } catch (InterruptedException e) {}
           
            }
            else // means timeLeft == 0
            { 
                Collections.shuffle(deck); // reshuffles the deck
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset){
            long restartTime = 60000;
            reshuffleTime = System.currentTimeMillis() + restartTime;
            env.ui.setCountdown(restartTime, false);
            env.ui.setElapsed(0);
        } else {
            long timeLeft = reshuffleTime - System.currentTimeMillis(); 
            boolean warn = timeLeft <= 5;
            env.ui.setCountdown(timeLeft, warn);
            env.ui.setElapsed(System.currentTimeMillis() - (reshuffleTime-60000));
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement - Yuval
        for (Integer slot : table.slotToCard) { 
            if (table.slotToCard[slot] != null) { // if slot is not empty
                deck.add(table.slotToCard[slot]); // add card back to deck 
                table.removeCard(slot); // remove card from slot on table 
                for (int player = 0; player < 12; player++) { // remove tokens from slot
                    if (table.playersTokensLocations[player][slot] == 1) {
                        table.removeToken(player, slot);
                    }
                }
            }
        }
        Collections.shuffle(deck); // shuffle deck 
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
                // Found a new maximum score, clear the winners list
                winners.clear();
                maxScore = playerScore;
                // Add the current player to the winners list
                winners.add(player.id);
            } else if (playerScore == maxScore) {
                // Another player has the same score as the current maximum, add them to winners list
                winners.add(player.id);
            }
        }
    
        // Convert the list of winners to an array
        int[] arrayOfWinners = winners.stream().mapToInt(Integer::intValue).toArray();
        
        // Notify the environment of the winners
        env.ui.announceWinner(arrayOfWinners);
    }
}
