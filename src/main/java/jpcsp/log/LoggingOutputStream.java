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

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jpcsp.log;

import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * An OutputStream that flushes out to a Logger.
 * <p>
 *
 * Note that no data is written out to the Logger until the stream is flushed
 * or closed.
 * <p>
 *
 * Example:
 *
 * <pre>
 * // make sure everything sent to System.err is logged
 * System.setErr(new PrintStream(new LoggingOutputStream(Category.getRoot(),
 * 		Priority.WARN), true));
 *
 * // make sure everything sent to System.out is also logged
 * System.setOut(new PrintStream(new LoggingOutputStream(Category.getRoot(),
 * 		Priority.INFO), true));
 * </pre>
 *
 * @author <a href="mailto://Jim.Moore@rocketmail.com">Jim Moore</a>
 * @see Logger
 */
public class LoggingOutputStream  {
	// @FIXME
}
