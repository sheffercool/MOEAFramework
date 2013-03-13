package org.moeaframework.util.progress;

import org.apache.commons.lang3.event.EventListenerSupport;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.moeaframework.Executor;

/**
 * Helper for notifying {@link ProgressListener}s when the evaluation progress
 * of an {@link Executor} changes.  This class reports the current progress,
 * percent complete, elapsed time, and remaining time.
 */
public class ProgressHelper {
	
	/**
	 * The listeners to receive progress reports.
	 */
	private final EventListenerSupport<ProgressListener> listeners;
	
	/**
	 * Calculates the moving average processing speed for estimating remaining
	 * time.
	 */
	private final DescriptiveStatistics statistics;
	
	/**
	 * The executor using this progress helper.
	 */
	private final Executor executor;
	
	/**
	 * The current seed being evaluated, starting at 1.
	 */
	private int currentSeed;
	
	/**
	 * The total number of seeds to be evaluated.
	 */
	private int totalSeeds;
	
	/**
	 * The current number of objective function evaluations for the current
	 * seed.
	 */
	private int currentNFE;
	
	/**
	 * The maximum number of objective function evaluations per seed.
	 */
	private int maxNFE;
	
	/**
	 * The time the {@link #start(int, int)} method was invoked.
	 */
	private long startTime;
	
	/**
	 * The last time that the {@link #updateStatistics()} method was invoked,
	 * used for estimating the remaining time.
	 */
	private long lastTime;
	
	/**
	 * The seed that was evaluated when {@link #updateStatistics()} was last
	 * invoked, used for estimating the remaining time.
	 */
	private int lastSeed;
	
	/**
	 * The NFE when {@link #updateStatistics()} was last invoked, used for
	 * estimating the remaining time.
	 */
	private int lastNFE;
	
	/**
	 * Constructs a new progress helper for generating progress reports for
	 * the given executor.
	 * 
	 * @param executor the executor that will be reporting progress
	 */
	public ProgressHelper(Executor executor) {
		super();
		this.executor = executor;
		
		statistics = new DescriptiveStatistics(25);
		listeners = EventListenerSupport.create(ProgressListener.class);
	}
	
	/**
	 * Adds the given listener to receive progress reports.
	 * 
	 * @param listener the listener to receive progress reports
	 */
	public void addProgressListener(ProgressListener listener) {
		listeners.addListener(listener);
	}
	
	/**
	 * Removes the given listener so it no longer receives progress reports.
	 * 
	 * @param listener the listener to no longer receive progress reports
	 */
	public void removeProgressListener(ProgressListener listener) {
		listeners.removeListener(listener);
	}
	
	/**
	 * Updates the moving average statistics used for estimating the remaining
	 * time.  The processing speed is measured by the NFE completed since this
	 * method was last invoked.
	 */
	private void updateStatistics() {
		long currentTime = System.currentTimeMillis();
		
		// update moving average
		double diffTime = currentTime - lastTime;
		double diffSeed = currentSeed - lastSeed;
		double diffNFE = currentNFE - lastNFE;
		double percentChange = (diffSeed + (diffNFE / maxNFE)) / totalSeeds;
		
		statistics.addValue(diffTime / percentChange);
		
		// finally, update the last values
		lastTime = currentTime;
		lastSeed = currentSeed;
		lastNFE = currentNFE;
	}
	
	/**
	 * Sends a progress report to all registered listeners.  Use the argument
	 * {@code isSeedFinished} to indicate that a seed has completed and
	 * results can be accessed from the executor.
	 * 
	 * @param isSeedFinished {@code true} if this report is being sent as a
	 *        result of a completed seed; {@code false} otherwise
	 */
	private void sendProgressEvent(boolean isSeedFinished) {
		long currentTime = System.currentTimeMillis();
		double remainingSeeds = totalSeeds - currentSeed;
		double remainingNFE = maxNFE - currentNFE;
		double percentRemaining = (remainingSeeds + (remainingNFE / maxNFE)) /
				totalSeeds;
		
		ProgressEvent event = new ProgressEvent(
				executor,
				currentSeed,
				totalSeeds,
				isSeedFinished,
				currentNFE,
				maxNFE,
				Math.max(1.0 - percentRemaining, 0.0),
				(currentTime - startTime) / 1000.0,
				(statistics.getMean() * percentRemaining) / 1000.0);
		
		listeners.fire().progressUpdate(event);
	}
	
	/**
	 * Sets the current number of objective function evaluations.  This method
	 * will generate a progress report.
	 * 
	 * @param currentNFE the current number of objective function evaluations
	 */
	public void setCurrentNFE(int currentNFE) {
		this.currentNFE = currentNFE;
		
		updateStatistics();
		sendProgressEvent(false);
	}
	
	/**
	 * Sets the current seed.  This call will have no affect if the current
	 * seed is unchanged.  This method will generate a progress report if the
	 * current seed changes.
	 * <p>
	 * <b>It is strongly recommended that {@link #nextSeed()} is used instead
	 * of this method, as {@code nextSeed()} sets the current NFE to 0.</b>
	 * 
	 * @param currentSeed the current seed being processed, starting at
	 *        {@code 1}
	 */
	public void setCurrentSeed(int currentSeed) {
		if (this.currentSeed != currentSeed) {
			this.currentSeed = currentSeed;
	
			updateStatistics();
			sendProgressEvent(true);
		}
	}
	
	/**
	 * Increments the current seed and sets NFE to 0.  This method will
	 * generate a progress report.  This method should be invoked after every
	 * seed completes in order to notify listeners that the seed completed.
	 */
	public void nextSeed() {
		currentSeed++;
		currentNFE = 0;
		
		updateStatistics();
		sendProgressEvent(true);
	}
	
	/**
	 * Prepares this progress helper for use.  This method must be invoked
	 * prior to calling all other methods.  The internal state of the progress
	 * helper is reset, allowing a single progress helper to be reused across
	 * many sequential runs.
	 * 
	 * @param totalSeeds the total number of seeds to be evaluated
	 * @param maxNFE the maximum number of objective function evaluations per
	 *        seed
	 */
	public void start(int totalSeeds, int maxNFE) {
		this.totalSeeds = totalSeeds;
		this.maxNFE = maxNFE;
		
		// reset all internal parameters
		lastTime = startTime;
		lastSeed = 1;
		lastNFE = 0;
		currentSeed = 1;
		currentNFE = 0;
		statistics.clear();
		startTime = System.currentTimeMillis();
	}
	
	/**
	 * Stops this progress helper.  No other methods should be invoked after
	 * calling this method.  However, {@link #start(int, int)} can be called
	 * to reset and restart this progress helper.
	 */
	public void stop() {
		// this currently does nothing, but may be used in the future if we
		// do anything to reduce the number of reports (i.e., send updates
		// at most once every second)
	}
	
	public static void main(String[] args) {
		int totalSeeds = 10;
		int maxNFE = 100000;
		ProgressHelper helper = new ProgressHelper(null);
		
		helper.addProgressListener(new ProgressListener() {

			@Override
			public void progressUpdate(ProgressEvent event) {
				System.out.println(event.getRemainingTime());
			}
			
		});
		
		helper.start(totalSeeds, maxNFE);
		
		for (int i = 0; i < totalSeeds; i++) {
			for (int j = 0; j <= maxNFE-10000; j += 10000) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					//do nothing
				}
				
				helper.setCurrentNFE(j+10000);
			}
			
			helper.nextSeed();
		}
	}

}