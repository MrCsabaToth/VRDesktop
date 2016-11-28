/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.freerdp.freerdpcore.presentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import com.freerdp.freerdpcore.R;
import com.freerdp.freerdpcore.application.WorldLayoutData;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Google VR sample application.
 * <p></p>
 * The TreasureHunt scene consists of a planar ground grid and a floating
 * "treasure" cube. When the user looks at the cube, the cube will turn gold.
 * While gold, the user can activate the Cardboard trigger, which will in turn
 * randomly reposition the cube.
 */
public class ScreenActivity extends GvrActivity implements GvrView.StereoRenderer {

  private static final String TAG = "TreasureHuntActivity";

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100.0f;

  private static final float CAMERA_Z = 0.01f;

  private static final int COORDS_PER_VERTEX = 3;

  // We keep the light always position just above the user.
  private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] {0.0f, 2.0f, 0.0f, 1.0f};

  private final float[] lightPosInEyeSpace = new float[4];

  private FloatBuffer screenVertices;
  private FloatBuffer screenNormals;
  private FloatBuffer screenTextureCoords;

  private int screenProgram;

  private int screenPositionParam;
  private int screenNormalParam;
  private int screenTextureCoordsParam;
  private int screenModelParam;
  private int screenModelViewParam;
  private int screenModelViewProjectionParam;
  private int screenTextureParam;

  private float[] camera;
  private float[] view;
  private float[] headView;
  private float[] modelViewProjection;
  private float[] modelView;
  private float[] modelScreen;

  private float screenDistance = 12f;
  private float screenWidth = 19.2f;
  private float screenHeight = 10.8f;

  // This is a handle to our texture data.
  private int textureDataHandle;

  private Vibrator vibrator;

  /**
   * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
   *
   * @param type The type of shader we will be creating.
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The shader object handler.
   */
  private int loadGLShader(int type, int resId) {
    String code = readRawTextFile(resId);
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);

    // Get the compilation status.
    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    // If the compilation failed, delete the shader.
    if (compileStatus[0] == 0) {
      Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }

    if (shader == 0) {
      throw new RuntimeException("Error creating shader.");
    }

    return shader;
  }

  /**
   * TextureHelper from http://www.learnopengles.com/
   * https://github.com/learnopengles/Learn-OpenGLES-Tutorials/
   * @param resourceId
   * @return reaource handle
   */
  public int loadTexture(final int resourceId)
  {
    final int[] textureHandle = new int[1];

    GLES20.glGenTextures(1, textureHandle, 0);

    if (textureHandle[0] != 0) {
      final BitmapFactory.Options options = new BitmapFactory.Options();
      options.inScaled = false;	// No pre-scaling

      // Read in the resource
      final Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId, options);

      // Bind to the texture in OpenGL
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

      // Set filtering
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST); // GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST); // GLES20.GL_LINEAR);

      // Load the bitmap into the bound texture.
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
      // TODO: ETC2 compression later: glCompressedTexImage2D
      // off-line: http://malideveloper.arm.com/resources/tools/mali-gpu-texture-compression-tool/
      // on-line: https://github.com/AndrewJo/ETCPack

      // Recycle the bitmap, since its data has been loaded into OpenGL.
      bitmap.recycle();
    }

    if (textureHandle[0] == 0) {
      throw new RuntimeException("Error loading texture.");
    }

    return textureHandle[0];
  }

  /**
   * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
   *
   * @param label Label to report in case of error.
   */
  private static void checkGLError(String label) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, label + ": glError " + error);
      throw new RuntimeException(label + ": glError " + error);
    }
  }

  /**
   * Sets the view to our GvrView and initializes the transformation matrices we will use
   * to render our scene.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    initializeGvrView();

    camera = new float[16];
    view = new float[16];
    modelViewProjection = new float[16];
    modelView = new float[16];
    modelScreen = new float[16];
    headView = new float[16];
    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
  }

  public void initializeGvrView() {
    setContentView(R.layout.common_ui);

    GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
    //gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

    gvrView.setRenderer(this);
    gvrView.setTransitionViewEnabled(true);

    if (gvrView.setAsyncReprojectionEnabled(true)) {
      // Async reprojection decouples the app framerate from the display framerate,
      // allowing immersive interaction even at the throttled clockrates set by
      // sustained performance mode.
      AndroidCompat.setSustainedPerformanceMode(this, true);
    }

    setGvrView(gvrView);
  }

  @Override
  public void onRendererShutdown() {
    Log.i(TAG, "onRendererShutdown");
  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    Log.i(TAG, "onSurfaceChanged");
  }

  /**
   * Creates the buffers we use to store information about the 3D world.
   *
   * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
   * Hence we use ByteBuffers.
   *
   * @param config The EGL configuration used when creating the surface.
   */
  @Override
  public void onSurfaceCreated(EGLConfig config) {
    Log.i(TAG, "onSurfaceCreated");
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

    float screenWidthHalf = screenWidth / 2.0f;
    float screenHeightHalf = screenHeight / 2.0f;
    float[] screenCoords = new float[] {
      -screenWidthHalf,  screenHeightHalf, 0.0f,
      -screenWidthHalf, -screenHeightHalf, 0.0f,
       screenWidthHalf,  screenHeightHalf, 0.0f,
      -screenWidthHalf, -screenHeightHalf, 0.0f,
       screenWidthHalf, -screenHeightHalf, 0.0f,
       screenWidthHalf,  screenHeightHalf, 0.0f,
    };

    float[] screenTextureCoordinates = new float[] {
      0.0f, 0.0f,
      0.0f, 1.0f,
      1.0f, 0.0f,
      0.0f, 1.0f,
      1.0f, 1.0f,
      1.0f, 0.0f,
    };

    // make a screen
    ByteBuffer bbScreenVertices = ByteBuffer.allocateDirect(screenCoords.length * 4);
    bbScreenVertices.order(ByteOrder.nativeOrder());
    screenVertices = bbScreenVertices.asFloatBuffer();
    screenVertices.put(screenCoords);
    screenVertices.position(0);

    ByteBuffer bbScreenNormals = ByteBuffer.allocateDirect(WorldLayoutData.SCREEN_NORMALS.length * 4);
    bbScreenNormals.order(ByteOrder.nativeOrder());
    screenNormals = bbScreenNormals.asFloatBuffer();
    screenNormals.put(WorldLayoutData.SCREEN_NORMALS);
    screenNormals.position(0);

    ByteBuffer bbScreenTextureCoords = ByteBuffer.allocateDirect(screenTextureCoordinates.length * 4);
    bbScreenTextureCoords.order(ByteOrder.nativeOrder());
    screenTextureCoords = bbScreenTextureCoords.asFloatBuffer();
    screenTextureCoords.put(screenTextureCoordinates);
    screenTextureCoords.position(0);

    int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.screen_vertex);
    int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.screen_fragment);

    screenProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(screenProgram, vertexShader);
    GLES20.glAttachShader(screenProgram, passthroughShader);
    GLES20.glLinkProgram(screenProgram);
    GLES20.glUseProgram(screenProgram);

    checkGLError("Screen program");

    screenPositionParam = GLES20.glGetAttribLocation(screenProgram, "a_Position");
    screenNormalParam = GLES20.glGetAttribLocation(screenProgram, "a_Normal");
    screenTextureCoordsParam = GLES20.glGetAttribLocation(screenProgram, "a_TexCoordinate");
    screenTextureParam = GLES20.glGetUniformLocation(screenProgram, "u_Texture");

    screenModelParam = GLES20.glGetUniformLocation(screenProgram, "u_Model");
    screenModelViewParam = GLES20.glGetUniformLocation(screenProgram, "u_MVMatrix");
    screenModelViewProjectionParam = GLES20.glGetUniformLocation(screenProgram, "u_MVP");

    GLES20.glEnableVertexAttribArray(screenPositionParam);
    GLES20.glEnableVertexAttribArray(screenNormalParam);
    GLES20.glEnableVertexAttribArray(screenTextureCoordsParam);

    checkGLError("Screen program params");

    // Screen appears directly in front of user.
    Matrix.setIdentityM(modelScreen, 0);
    Matrix.translateM(modelScreen, 0, 0, 0, -screenDistance);

    // Load the texture
    textureDataHandle = loadTexture(R.drawable.full_screen_shot_1);

    checkGLError("onSurfaceCreated");
  }

  /**
   * Converts a raw text file into a string.
   *
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The context of the text file, or null in case of error.
   */
  private String readRawTextFile(int resId) {
    InputStream inputStream = getResources().openRawResource(resId);
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Prepares OpenGL ES before we draw a frame.
   *
   * @param headTransform The head transformation in the new frame.
   */
  @Override
  public void onNewFrame(HeadTransform headTransform) {
    // Build the camera matrix and apply it to the ModelView.
    Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

    headTransform.getHeadView(headView, 0);

    checkGLError("onReadyToDraw");
  }

  /**
   * Draws a frame for an eye.
   *
   * @param eye The eye to render. Includes all required transformations.
   */
  @Override
  public void onDrawEye(Eye eye) {
    GLES20.glEnable(GLES20.GL_CULL_FACE);
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    checkGLError("onDrawEye");

    // Apply the eye transformation to the camera.
    Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

    // Set the position of the light
    Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

    // Build the ModelView and ModelViewProjection matrices
    // for calculating cube position and light.
    float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

    // Set modelView for the screen, so we draw screen in the correct location
    Matrix.multiplyMM(modelView, 0, view, 0, modelScreen, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
    drawScreen();
  }

  @Override
  public void onFinishFrame(Viewport viewport) {}

  /**
   * Draw the screen.
   *
   * <p>This feeds in data for the screen into the shader. Note that this doesn't feed in data about
   * position of the light, so if we rewrite our code to draw the screen first, the lighting might
   * look strange.
   */
  public void drawScreen() {
    GLES20.glUseProgram(screenProgram);

    // Set ModelView, MVP, position, normals, and texture coordinates.
    GLES20.glUniformMatrix4fv(screenModelParam, 1, false, modelScreen, 0);
    GLES20.glUniformMatrix4fv(screenModelViewParam, 1, false, modelView, 0);
    GLES20.glUniformMatrix4fv(screenModelViewProjectionParam, 1, false,
            modelViewProjection, 0);
    GLES20.glVertexAttribPointer(screenPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, screenVertices);

    GLES20.glVertexAttribPointer(screenNormalParam, 3, GLES20.GL_FLOAT, false, 0,
            screenNormals);
    GLES20.glVertexAttribPointer(screenTextureCoordsParam, 2, GLES20.GL_FLOAT, false, 0, screenTextureCoords);

    // Set the active texture unit to texture unit 0.
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    // Bind the texture to this unit.
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureDataHandle);
    // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
    GLES20.glUniform1i(screenTextureParam, 0);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

    checkGLError("drawing screen");
  }

  /**
   * Called when the Cardboard trigger is pulled.
   */
  @Override
  public void onCardboardTrigger() {
    Log.i(TAG, "onCardboardTrigger");

    // Always give user feedback.
    vibrator.vibrate(50);
  }

}
