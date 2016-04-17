/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.modulator.Click;
import heronarts.lx.parameter.BasicParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;

/**
 * Class to represent a musical tempo at which patterns are operating. This can
 * be updated in real-time via invoking the tap() method.
 *
 * Quarter-note "beats" are indicated by the return value of the beat() method,
 * and ramp() returns a double value indicating the current 4/4 phase from 0 to
 * 1.
 */
public class Tempo extends LXComponent {

  public interface Listener {
    public void onBeat(Tempo tempo);
    public void onHalf(Tempo tempo);
    public void onMeasure(Tempo tempo);
  }

  private final static double MINUTE = 60000;
  private final static double DEFAULT_BPM = 120;
  public final static double MIN_BPM = 20;
  public final static double MAX_BPM = 240;

  public final BasicParameter bpm = new BasicParameter("BPM", DEFAULT_BPM, MIN_BPM, MAX_BPM);

  private final MutableParameter period = new MutableParameter(MINUTE / DEFAULT_BPM);

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final Click click = new Click(period);

  private long firstTap = 0;
  private long lastTap = 0;
  private int tapCount = 0;

  private int beatCount = 0;
  private boolean triggered = false;

  public Tempo(LX lx) {
    super(lx);
    addParameter(this.bpm);
    addModulator(this.click).start();
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    if (parameter == this.bpm) {
      this.period.setValue(MINUTE / this.bpm.getValue());
    }
  }

  public Tempo addListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public Tempo removeListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  /**
   * Method to indicate when we are on-beat, assuming quarter-notes being given
   * one beat.
   *
   * @return true if we are on a quarter-note beat
   */
  public boolean beat() {
    return this.click.click();
  }

  /**
   * Method to indicate when we are on-beat, for half a measure.
   *
   * @return true if we are on a half-note beat
   */
  public boolean half() {
    return beat() && (this.beatCount % 2 == 0);
  }

  /**
   * Method to indicate the start of a measure.
   *
   * @return true if we are on a measure-beat
   */
  public boolean measure() {
    return beat() && (this.beatCount % 4 == 0);
  }

  /**
   * Indicates phase of the current beat. On the beat the value will be 0, then
   * ramp up to 1 before the next beat triggers.
   *
   * @return value from 0-1 indicating phase of beat
   */
  public double ramp() {
    return this.click.getBasis();
  }

  /**
   * Indicates beat phase in floating point
   *
   * @return value from 0-1 indicating phase of beat
   */
  public float rampf() {
    return (float) this.ramp();
  }

  /**
   * Returns the current tempo in Beats Per Minute
   *
   * @return Current tempo
   */
  public double bpm() {
    return this.bpm.getValue();
  }

  /**
   * Returns the tempo in floating point
   *
   * @return Current tempo in float
   */
  public float bpmf() {
    return (float) this.bpm();
  }

  /**
   * Sets the BPM to the given value
   *
   * @param bpm Number of beats per minute
   * @return this
   */
  public Tempo setBpm(double bpm) {
    this.bpm.setValue(bpm);
    return this;
  }

  /**
   * Adjust the BPM by the given amount
   *
   * @param amount Amount to adjust BPM by
   * @return this
   */
  public Tempo adjustBpm(double amount) {
    this.bpm.setValue(this.bpm.getValue() + amount);
    return this;
  }

  /**
   * Re-triggers the metronome, so that it immediately beats.
   */
  public void trigger() {
    this.beatCount = 0;
    this.click.fire();
    this.triggered = true;
  }

  /**
   * Adjusts the tempo in realtime by tapping. Whenever tap() is invoked the
   * time between previous taps is averaged to compute a new tempo. At least
   * three taps are required to compute a tempo. Otherwise, tapping just
   * re-triggers the beat. It is better to use the trigger() method directly if
   * this is all that is desired.
   */
  public void tap() {
    long now = System.currentTimeMillis();
    if (now - this.lastTap > 2000) {
      this.firstTap = now;
      this.tapCount = 0;
    }
    this.lastTap = now;
    ++this.tapCount;
    if (this.tapCount > 3) {
      double beatPeriod = (this.lastTap - this.firstTap) / (double) (this.tapCount - 1);
      setBpm(MINUTE / beatPeriod);
    }
    trigger();
  }

  /**
  * If smoothing is disabled, tap(false) infers the beat period from the time
  * difference between the current tap and the last tap. Does not re-trigger
  * the beat when invoked. This approach should be used to stay synced with
  * an external tempo-keeping mechanism, not a human.
  */
  public void tap(boolean smoothingEnabled) {
    if (!smoothingEnabled) {
      long now = System.currentTimeMillis();
      this.firstTap = lastTap;
      this.lastTap = now;

      double beatPeriod = this.lastTap - this.firstTap;
      setBpm(MINUTE / beatPeriod);
    } else {
      tap();
    }
  }

  @Override
  public void loop(double deltaMs) {
    super.loop(deltaMs);
    if (beat() && !triggered) {
      ++this.beatCount;
    }
    this.triggered = false;
  }
}
