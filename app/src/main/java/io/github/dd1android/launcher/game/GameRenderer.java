package io.github.dd1android.launcher.game;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import io.github.dd1android.launcher.xserver.Drawable;
import io.github.dd1android.launcher.xserver.Window;
import io.github.dd1android.launcher.xserver.WindowManager;
import io.github.dd1android.launcher.xserver.XLock;
import io.github.dd1android.launcher.xserver.XServer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class GameRenderer implements GLSurfaceView.Renderer,
        WindowManager.OnWindowModificationListener {
    private static final float[] QUAD = {-1, -1, 0, 1, -1, 1, 0, 0,
            1, -1, 1, 1, 1, 1, 1, 0};
    private final GLSurfaceView view;
    private final XServer xServer;
    private final Runnable onReady;
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(QUAD.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int program;

    GameRenderer(GLSurfaceView view, XServer xServer, Runnable onReady) {
        this.view = view;
        this.xServer = xServer;
        this.onReady = onReady;
        vertices.put(QUAD).rewind();
        xServer.windowManager.addOnWindowModificationListener(this);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        program = link("attribute vec2 p; attribute vec2 uv; varying vec2 v;"
                        + "void main(){v=uv;gl_Position=vec4(p,0.,1.);}",
                "precision mediump float; varying vec2 v; uniform sampler2D image;"
                        + "void main(){gl_FragColor=texture2D(image,v);}");
        GLES20.glClearColor(0.025f, 0.055f, 0.09f, 1f);
        onReady.run();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        try (XLock ignored = xServer.lock(
                XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            Window window = largestMapped(xServer.windowManager.rootWindow, null);
            if (window == null) return;
            Drawable drawable = window.getContent();
            synchronized (drawable.renderLock) {
                drawable.getTexture().updateFromDrawable();
                GLES20.glUseProgram(program);
                int position = GLES20.glGetAttribLocation(program, "p");
                int uv = GLES20.glGetAttribLocation(program, "uv");
                vertices.position(0);
                GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices);
                vertices.position(2);
                GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices);
                GLES20.glEnableVertexAttribArray(position);
                GLES20.glEnableVertexAttribArray(uv);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, drawable.getTexture().getTextureId());
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            }
        }
    }

    private static Window largestMapped(Window window, Window best) {
        if (window.attributes.isViewable()
                && (best == null || window.getWidth() * window.getHeight()
                >= best.getWidth() * best.getHeight())) best = window;
        for (Window child : window.getChildren()) best = largestMapped(child, best);
        return best;
    }

    private static int link(String vertex, String fragment) {
        int program = GLES20.glCreateProgram();
        int vertexId = compile(GLES20.GL_VERTEX_SHADER, vertex);
        int fragmentId = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
        GLES20.glAttachShader(program, vertexId);
        GLES20.glAttachShader(program, fragmentId);
        GLES20.glLinkProgram(program);
        return program;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override public void onMapWindow(Window window) { view.requestRender(); }
    @Override public void onUnmapWindow(Window window) { view.requestRender(); }
    @Override public void onChangeWindowZOrder(Window window) { view.requestRender(); }
    @Override public void onUpdateWindowContent(Window window) { view.requestRender(); }
    @Override public void onUpdateWindowGeometry(Window window, boolean resized) { view.requestRender(); }
}
