#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>

#define TAG "DD1/Runtime"

static ANativeWindow *window;
static EGLDisplay display = EGL_NO_DISPLAY;
static EGLSurface surface = EGL_NO_SURFACE;
static EGLContext context = EGL_NO_CONTEXT;

static void detach(void) {
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        eglTerminate(display);
    }
    if (window) ANativeWindow_release(window);
    window = NULL;
    display = EGL_NO_DISPLAY;
    surface = EGL_NO_SURFACE;
    context = EGL_NO_CONTEXT;
}

JNIEXPORT jboolean JNICALL
Java_io_github_dd1android_launcher_runtime_NativeRuntime_attachSurface(
        JNIEnv *env, jclass clazz, jobject java_surface) {
    (void) clazz;
    detach();
    window = ANativeWindow_fromSurface(env, java_surface);
    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (!window || display == EGL_NO_DISPLAY || !eglInitialize(display, NULL, NULL)) goto fail;

    EGLConfig config;
    EGLint count;
    const EGLint config_attributes[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
            EGL_NONE};
    const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    if (!eglChooseConfig(display, config_attributes, &config, 1, &count) || count != 1) goto fail;
    surface = eglCreateWindowSurface(display, config, window, NULL);
    context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attributes);
    if (surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT ||
        !eglMakeCurrent(display, surface, surface, context)) goto fail;

    glClearColor(0.025f, 0.055f, 0.09f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (!eglSwapBuffers(display, surface)) goto fail;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Surface bridge ready: %dx%d",
                        ANativeWindow_getWidth(window), ANativeWindow_getHeight(window));
    return JNI_TRUE;

fail:
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Surface bridge failed: EGL 0x%x", eglGetError());
    detach();
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_dd1android_launcher_runtime_NativeRuntime_detachSurface(
        JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    detach();
}
