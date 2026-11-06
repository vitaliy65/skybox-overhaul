package skybox.overhaul.client;

import org.joml.Vector4f;

/** Last fog values vanilla computed this frame (render thread only). */
public final class SkyboxState {
	public static final Vector4f FOG = new Vector4f();
	public static int renderDistanceChunks = 12;
	public static boolean valid;

	private SkyboxState() {}
}
