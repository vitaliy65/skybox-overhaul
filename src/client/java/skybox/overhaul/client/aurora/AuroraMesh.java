package skybox.overhaul.client.aurora;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CPU-side vertex sink for the aurora: POSITION_COLOR quads (3 floats + 4 colour bytes = 16 bytes per vertex)
 * written straight into a reusable direct buffer. No per-vertex objects, no sorting (the aurora is blended
 * additively, so draw order does not matter).
 */
final class AuroraMesh {
	static final int VERTEX_BYTES = 16;

	private ByteBuffer buf = ByteBuffer.allocateDirect(1 << 20).order(ByteOrder.nativeOrder());
	private int vertices;

	void clear() {
		buf.clear();
		vertices = 0;
	}

	/** Makes room for at least {@code bytes} more bytes (grows by doubling, keeps what was written). */
	void ensure(int bytes) {
		if (buf.remaining() >= bytes) return;
		ByteBuffer bigger = ByteBuffer.allocateDirect(Math.max(buf.capacity() * 2, buf.position() + bytes))
			.order(ByteOrder.nativeOrder());
		buf.flip();
		bigger.put(buf);
		buf = bigger;
	}

	void vertex(float x, float y, float z, byte r, byte g, byte b, byte a) {
		buf.putFloat(x).putFloat(y).putFloat(z).put(r).put(g).put(b).put(a);
		vertices++;
	}

	int quads() {
		return vertices / 4;
	}

	/** The written vertices, ready for createBuffer (independent position/limit, the scratch buffer stays reusable). */
	ByteBuffer data() {
		ByteBuffer d = buf.duplicate().order(ByteOrder.nativeOrder());
		d.flip();
		return d;
	}
}
