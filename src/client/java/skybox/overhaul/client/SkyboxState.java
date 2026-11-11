package skybox.overhaul.client;

import org.joml.Vector4f;

/** Last fog values vanilla computed this frame (render thread only). */
public final class SkyboxState {
	public static final Vector4f FOG = new Vector4f();
	/** 1 = clear, 0 = full rain; our sky layers fade out with rain so weather stays vanilla. */
	public static float rainBrightness = 1.0F;
	/** Vanilla sun angle in radians (0 = noon), for the twilight glow. */
	public static float sunAngle;
	/** Actual weather strength: 0 = clear, 1 = full rain. */
	public static float rainLevel;
	/** Thunderstorm strength: 0 = none, 1 = full thunder (clouds go much darker). */
	public static float thunderLevel;
	/** World clock in ticks, for time-based effects that must not depend on frame rate (morning fog). */
	public static long gameTime;
	/** False in dimensions without our sky (Nether, End, no sky): morning fog stays off there. */
	public static boolean skyboxActive;
	/** 0..1 pulse from vanilla lightning; zero when the player has hidden sky flashes. */
	public static float lightningFlash;
	public static boolean valid;

	private SkyboxState() {}
}
