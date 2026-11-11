package skybox.overhaul.client.render;

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
	/** Thunder strength for storm cloud layers. */
	public static float thunderLevel;
	/** World time used by weather transitions. */
	public static long gameTime;
	/** Whether the current dimension has a normal sky. */
	public static boolean skyboxActive;
	/** 0..1 short-lived lightning flash intensity for the current frame. */
	public static float lightningFlash;
	public static boolean valid;

	private SkyboxState() {}
}
