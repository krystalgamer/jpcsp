/*
 This file is part of jpcsp.

 Jpcsp is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Jpcsp is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Jpcsp.  If not, see <http://www.gnu.org/licenses/>.
 */
package jpcsp.graphics;

import static jpcsp.HLE.modules.sceDisplay.getTexturePixelFormat;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLX;
import org.lwjgl.opengl.WGL;
import org.lwjgl.system.Platform;
import org.lwjgl.system.linux.X11;
import org.lwjgl.system.linux.XVisualInfo;
import org.lwjgl.system.windows.User32;

import jpcsp.Allegrex.compiler.RuntimeContext;
import jpcsp.HLE.Modules;
import jpcsp.HLE.modules.sceDisplay;
import jpcsp.graphics.RE.IRenderingEngine;
import jpcsp.graphics.textures.FBTexture;

/**
 * Thread rendering the video command lists submitted by the sceGe_user module.
 * 
 * @author gid15
 *
 */
public class VideoEngineThread extends Thread {
	private static Logger log = VideoEngine.log;
	private final VideoEngine videoEngine;
    private Semaphore update = new Semaphore(1);
    private boolean run = true;
    private long videoEngineContext;

    public static boolean isActive() {
    	return Platform.get() == Platform.WINDOWS;
    }

    public VideoEngineThread(VideoEngine videoEngine) {
		this.videoEngine = videoEngine;

		long currentContext = WGL.wglGetCurrentContext();
		long displayWindow = Modules.sceDisplayModule.getCanvas().getDisplayWindow();
		switch (Platform.get()) {
			case WINDOWS:
				long dc = User32.GetDC(displayWindow);
				videoEngineContext = WGL.wglCreateContext(dc);
				if (!WGL.wglShareLists(currentContext, videoEngineContext)) {
					log.error(String.format("Cannot share context 0x%X with 0x%X", currentContext, videoEngineContext));
				}
				User32.ReleaseDC(displayWindow, dc);
				break;
			case LINUX:
				// TODO Not yet working for Linux
				int screen = X11.XDefaultScreen(displayWindow);
				if (log.isDebugEnabled()) {
					log.debug(String.format("XDefaultScreen displayWindow=0x%X, screen=%d", displayWindow, screen));
				}
				XVisualInfo visualInfo = GLX.glXChooseVisual(displayWindow, screen, new int[] { 0 });
				long glxCurrentContext = GLX.glXGetCurrentContext();
				if (log.isDebugEnabled()) {
					log.debug(String.format("glxCurrentContext=0x%X", glxCurrentContext));
				}
				videoEngineContext = GLX.glXCreateContext(displayWindow, visualInfo, glxCurrentContext, true);
				if (log.isDebugEnabled()) {
					log.debug(String.format("videoEngineContext=0x%X", videoEngineContext));
				}
				break;
			default:
				log.error(String.format("Unsupported platform %s", Platform.get().getName()));
				break;
		}
	}

	@Override
	public void run() {
		final IRenderingEngine re = videoEngine.getRenderingEngine();
		final sceDisplay displayModule = Modules.sceDisplayModule;
		RuntimeContext.setLog4jMDC();

		if (log.isDebugEnabled()) {
			log.debug(String.format("Start of Video Engine Thread with videoEngineContext=0x%X", videoEngineContext));
		}

		long displayWindow = displayModule.getCanvas().getDisplayWindow();
		switch (Platform.get()) {
			case WINDOWS:
				long dc = User32.GetDC(displayWindow);
				if (!WGL.wglMakeCurrent(dc, videoEngineContext)) {
					log.error(String.format("Cannot make context 0x%X current", videoEngineContext));
				}
				User32.ReleaseDC(displayWindow, dc);
				break;
			case LINUX:
				// TODO Not yet working for Linux
				if (!GLX.glXMakeCurrent(displayWindow, displayModule.getCanvas().getDisplayDrawable(), videoEngineContext)) {
					log.error(String.format("Cannot make context 0x%X current", videoEngineContext));
				}
				break;
			default:
				log.error(String.format("Unsupported platform %s", Platform.get().getName()));
				break;
		}

        GL.createCapabilities();

        re.startDisplay();

        FBTexture renderTexture = new FBTexture(displayModule.getTopAddrFb(), displayModule.getBufferWidthFb(), displayModule.getWidthFb(), displayModule.getHeightFb(), getTexturePixelFormat(displayModule.getPixelFormatFb()));

		while (run) {
			waitForUpdate();

			displayModule.lockDisplay();
			renderTexture.bind(re, false);

            videoEngine.update();

            displayModule.unlockDisplay();
		}
	}

    public void update() {
    	update.release();
    }

    private void waitForUpdate() {
        while (true) {
            try {
                int availablePermits = update.drainPermits();
                if (availablePermits > 0) {
                    break;
                }

                if (update.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
            }
        }
    }

    public void exit() {
        run = false;
        update();
    }
}
