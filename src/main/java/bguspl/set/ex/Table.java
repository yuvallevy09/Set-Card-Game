package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    protected Integer[][] playersTokensLocations; //Added by tomer, Mapping between a player token and it place on the table.

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;

        playersTokensLocations = new Integer[env.config.players][12]; // Creates an 2D array in size of the numbers of players * num of slots on table
        for(int i = 0; i <= env.config.players; i++)
        {
            for(int j = 0; j < 12; j++)
            {
                playersTokensLocations[i][j] = -1; //Initialize all cells to -1, means no token is set on the table yet
            }
        } 
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }


    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement - Tomer
        env.ui.placeCard(card, slot); //Display the card image on the table according to the slot
        
        
        //Check if needs to remove the card from the deck here or its happening in the Dealer's file.
        //check to see if anything else needs to be added here
        // check to see if before we match the card to slot and opp we need to check if this location is null
        
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement - Tomer
        int cardToRemove = slotToCard[slot];
        env.ui.removeCard(slot); //removes the card image from the table
        slotToCard[slot] = null; //removes the card from the slot
        cardToSlot[cardToRemove] = null; // removes the slot the card was
        // needs to remove tokens on corresponding card 
        //Check to see if anything else needs to be added here
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        if(playersTokensLocations[player][slot] == -1) // if there is no token of this player on this slot
        {
            playersTokensLocations[player][slot] = 1; // marks the slot with the player's token
            env.ui.placeToken(player, slot); //places the token with the player's name on slot he chose
            
        } 
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement - Tomer
        if(playersTokensLocations[player][slot] != -1) //if there is a token on this slot
        {
            env.ui.removeToken(player, slot);
            playersTokensLocations[player][slot] = -1; //removes the token from the array
            return true;
        }
        return false;
    }
}
