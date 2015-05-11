package org.jikesrvm.replay.sys.ditto;

import java.util.HashMap;
import java.util.Map;

import org.vmmagic.pragma.Inline;

/**
 * A thread interaction table holds a record of the interactions between the
 * thread that holds it and other threads. This record allows the replay
 * subsystem to perform transitive reduction and thus reduce the amount of
 * explicit synchronization required to replay an execution.
 * <p>
 * An interaction between threads is a point in logical time in which they must
 * synchronize to perform a synchronization or memory access operation.
 * <p>
 * When the thread holding the table performs an operation that must be
 * synchronized with another thread T, which performed an operation on the
 * same object/field when its logical clock was C, it may register an
 * interaction in the table, stating that its last interaction with thread X
 * was at time C.
 */
public final class ThreadInteractionTable {

  /**
   * Maps thread identifiers to the last known thread interaction that the
   * thread holding this table had with the threads with those ids.
   */
  private Map<Long, ThreadInteraction> interactions;

  /** Constructor. */
  public ThreadInteractionTable() {
    interactions = new HashMap<Long, ThreadInteraction>();
  }

  /**
   * Registers an interaction with a thread at a given logical time.
   * @param theirId    id of the thread
   * @param theirClock logical clock of the thread at the interaction point
   */
  @Inline
  public void registerInteraction(long theirId, long theirClock) {
    ThreadInteraction ti = interactions.get(theirId);
    if (ti != null) {
      // only update the last interaction with this thread if we don't know of
      // another interaction that occurred at an higher logical time
      if (ti.theirClock < theirClock) {
        ti.theirClock = theirClock;
      }
    } else {
      // no need for synchronization, the table is only accessed by the thread
      // to which it belongs
      ti = new ThreadInteraction();
      ti.theirClock = theirClock;
      interactions.put(theirId, ti);
    }
  }

  /**
   * Gets the last known interaction with a thread.
   * @param theirId id of the thread
   * @return last known interaction with the thread with id {@code theirId}
   */
  @Inline
  public ThreadInteraction getInteractionWith(long theirId) {
    ThreadInteraction ti = interactions.get(theirId);
    if (ti != null) {
      return ti;
    } else {
      ti = new ThreadInteraction();
      interactions.put(theirId, ti);
      return ti;
    }
  }


  //
  // The following version of registerInteration propagates the last
  // interactions in the the thread's interactions table to our own table.
  // As a result, it enables better pruning and less synchronization during
  // replay.
  //
  // In practice, however, the replay performance gain does not outweight the
  // performance penalty that the table propagation causes during recording.
  // Furthermore, the replay performance is much less critical than the
  // record performance.
  //

  /*
  public void registerInteraction2(long theirId, long theirClock,
                                   ThreadInteractionTable theirTable, long myId,
                                   long myClock) {
    updateInteraction(theirId, theirClock, myClock);
    // Copy from their table. There is no need for synchronization.
    // ConcurrentHashMap will not throw ConcurrentModificationExceptions and we
    // do not need perfect consistency!
    for (Entry<Long, ThreadInteraction> e : interactions.entrySet()) {
      long thirdPartyId = e.getKey();
      if (thirdPartyId != myId) {
        ThreadInteraction interaction = e.getValue();
        // Copy only interactions that occurred before my own interaction.
        if (interaction.myClock <= theirClock) {
          updateInteraction(thirdPartyId, interaction.theirClock, myClock);
        }
      }
    }
  }

  private void updateInteraction(long theirId, long theirClock, long myClock) {
    ThreadInteraction directInteraction = getDirectInteraction(theirId);
    if (directInteraction.theirClock < theirClock) {
      directInteraction.theirClock = theirClock;
      directInteraction.myClock = myClock;
    }
  }

  private ThreadInteraction getDirectInteraction(long threadId) {
    if (interactions.containsKey(threadId)) {
      return interactions.get(threadId);
    } else {
      ThreadInteraction i = new ThreadInteraction();
      // Thread safe. Only I can modify the interactions!
      interactions.put(threadId, i);
      return i;
    }
  }
  */

}
