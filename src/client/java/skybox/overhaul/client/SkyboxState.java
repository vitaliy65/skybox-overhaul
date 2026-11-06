package skybox.overhaul.client;

import org.joml.Vector4f;

/** Last fog values vanilla computed this frame (render thread only). */
public final class SkyboxState {
	public static final Vector4f FOG = new Vector4f();
	/** 1 = clear, 0 = full rain; our sky layers fade out with rain so weather stays vanilla. */
	public static float rainBrightness = 1.0F;
	public static boolean valid;

	private SkyboxState() {}
}
