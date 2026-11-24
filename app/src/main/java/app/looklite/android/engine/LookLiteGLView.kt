package app.looklite.android.engine

import android.content.Context
import android.opengl.GLSurfaceView

class LookLiteGLView(context: Context, val renderer: LookLiteRenderer) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}
