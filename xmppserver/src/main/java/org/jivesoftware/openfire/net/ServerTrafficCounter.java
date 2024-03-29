/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2018 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.stats.StatisticsManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A ServerTrafficCounter counts the number of bytes read and written by the server. This
 * includes client-server, server-server, external components and connection managers traffic.
 * Note that traffic is monitored only for entities that are directly connected to the server.
 * However, traffic generated by file transfers is not considered unless files were sent using
 * the in-band method.
 *
 * @author Gaston Dombiak
 */
public class ServerTrafficCounter {

    /**
     * Outgoing server traffic counter.
     */
    private static final AtomicLong outgoingCounter = new AtomicLong(0);
    /**
     * Incoming server traffic counter.
     */
    private static final AtomicLong incomingCounter = new AtomicLong(0);

    private static final String trafficStatGroup = "server_bytes";
    private static final String incomingStatKey = "server_bytes_in";
    private static final String outgoingStatKey = "server_bytes_out";

    /**
     * Creates and adds statistics to statistic manager.
     */
    public static void initStatistics() {
        addReadBytesStat();
        addWrittenBytesStat();
    }

    /**
     * Wraps the specified input stream to count the number of bytes that were read.
     *
     * @param originalStream the input stream to wrap.
     * @return The wrapped input stream over the original stream.
     */
    public static InputStream wrapInputStream(InputStream originalStream) {
        return new InputStreamWrapper(originalStream);
    }

    /**
     * Wraps the specified output stream to count the number of bytes that were written.
     *
     * @param originalStream the output stream to wrap.
     * @return The wrapped output stream over the original stream.
     */
    public static OutputStream wrapOutputStream(OutputStream originalStream) {
        return new OutputStreamWrapper(originalStream);
    }

    /**
     * Wraps the specified readable channel to count the number of bytes that were read.
     *
     * @param originalChannel the readable byte channel to wrap.
     * @return The wrapped readable channel over the original readable channel .
     */
    public static ReadableByteChannel wrapReadableChannel(ReadableByteChannel originalChannel) {
        return new ReadableByteChannelWrapper(originalChannel);
    }

    /**
     * Wraps the specified writable channel to count the number of bytes that were written.
     *
     * @param originalChannel the writable byte channel to wrap.
     * @return The wrapped writable channel over the original writable channel .
     */
    public static WritableByteChannel wrapWritableChannel(WritableByteChannel originalChannel) {
        return new WritableByteChannelWrapper(originalChannel);
    }

    /**
     * Increments the counter of read bytes by delta.
     *
     * @param delta the delta of bytes that were read.
     */
    public static void incrementIncomingCounter(long delta) {
        incomingCounter.getAndAdd(delta);
    }

    /**
     * Increments the counter of written bytes by delta.
     *
     * @param delta the delta of bytes that were written.
     */
    public static void incrementOutgoingCounter(long delta) {
        outgoingCounter.getAndAdd(delta);
    }

    private static void addReadBytesStat() {
        // Register a statistic.
        Statistic statistic = new Statistic() {
            @Override
            public String getName() {
                return LocaleUtils.getLocalizedString("server_bytes.stats.incoming.name");
            }

            @Override
            public Type getStatType() {
                return Type.rate;
            }

            @Override
            public String getDescription() {
                return LocaleUtils.getLocalizedString("server_bytes.stats.incoming.description");
            }

            @Override
            public String getUnits() {
                return LocaleUtils.getLocalizedString("server_bytes.stats.incoming.label");
            }

            @Override
            public double sample() {
                // Divide result by 1024 so that we return the result in Kb.
                return incomingCounter.getAndSet(0)/1024d;
            }

            @Override
            public boolean isPartialSample() {
                return true;
            }
        };
        StatisticsManager.getInstance()
                .addMultiStatistic(incomingStatKey, trafficStatGroup, statistic);
    }

    private static void addWrittenBytesStat() {
        // Register a statistic.
        Statistic statistic = new Statistic() {
            @Override
            public String getName() {
                return LocaleUtils.getLocalizedString("server_bytes.stats.outgoing.name");
            }

            @Override
            public Type getStatType() {
                return Type.rate;
            }

            @Override
            public String getDescription() {
                return LocaleUtils.getLocalizedString("server_bytes.stats.outgoing.description");
            }

            @Override
            public String getUnits() {
                return LocaleUtils.getLocalizedString("server_bytes.stats.outgoing.label");
            }

            @Override
            public double sample() {
                return outgoingCounter.getAndSet(0)/1024d;
            }

            @Override
            public boolean isPartialSample() {
                return true;
            }
        };
        StatisticsManager.getInstance()
                .addMultiStatistic(outgoingStatKey, trafficStatGroup, statistic);
    }

    /**
     * Wrapper on an input stream to intercept and count number of read bytes.
     */
    private static class InputStreamWrapper extends InputStream {
        /**
         * Original input stream being wrapped to count incmoing traffic.
         */
        private InputStream originalStream;

        public InputStreamWrapper(InputStream originalStream) {
            this.originalStream = originalStream;
        }

        @Override
        public int read() throws IOException {
            int readByte = originalStream.read();
            if (readByte > -1) {
                incrementIncomingCounter(1);
            }
            return readByte;
        }

        @Override
        public int read(byte b[]) throws IOException {
            int bytes = originalStream.read(b);
            if (bytes > -1) {
                incrementIncomingCounter(bytes);
            }
            return bytes;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            int bytes = originalStream.read(b, off, len);
            if (bytes > -1) {
                incrementIncomingCounter(bytes);
            }
            return bytes;
        }

        @Override
        public int available() throws IOException {
            return originalStream.available();
        }

        @Override
        public void close() throws IOException {
            originalStream.close();
        }

        @Override
        public synchronized void mark(int readlimit) {
            originalStream.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return originalStream.markSupported();
        }

        @Override
        public synchronized void reset() throws IOException {
            originalStream.reset();
        }

        @Override
        public long skip(long n) throws IOException {
            return originalStream.skip(n);
        }
    }

    /**
     * Wrapper on an output stream to intercept and count number of written bytes.
     */
    private static class OutputStreamWrapper extends OutputStream {
        /**
         * Original output stream being wrapped to count outgoing traffic.
         */
        private OutputStream originalStream;

        public OutputStreamWrapper(OutputStream originalStream) {
            this.originalStream = originalStream;
        }

        @Override
        public void write(int b) throws IOException {
            // forward request to wrapped stream
            originalStream.write(b);
            // update outgoingCounter
            incrementOutgoingCounter(1);
        }

        @Override
        public void write(byte b[]) throws IOException {
            // forward request to wrapped stream
            originalStream.write(b);
            // update outgoingCounter
            incrementOutgoingCounter(b.length);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            // forward request to wrapped stream
            originalStream.write(b, off, len);
            // update outgoingCounter
            incrementOutgoingCounter(b.length);
        }

        @Override
        public void close() throws IOException {
            originalStream.close();
        }

        @Override
        public void flush() throws IOException {
            originalStream.flush();
        }
    }

    /**
     * Wrapper on a ReadableByteChannel to intercept and count number of read bytes.
     */
    private static class ReadableByteChannelWrapper implements ReadableByteChannel {
        private ReadableByteChannel originalChannel;

        public ReadableByteChannelWrapper(ReadableByteChannel originalChannel) {
            this.originalChannel = originalChannel;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int bytes = originalChannel.read(dst);
            if (bytes > -1) {
                incrementIncomingCounter(bytes);
            }
            return bytes;
        }

        @Override
        public void close() throws IOException {
            originalChannel.close();
        }

        @Override
        public boolean isOpen() {
            return originalChannel.isOpen();
        }
    }

    /**
     * Wrapper on a WritableByteChannel to intercept and count number of written bytes.
     */
    private static class WritableByteChannelWrapper implements WritableByteChannel {
        private WritableByteChannel originalChannel;

        public WritableByteChannelWrapper(WritableByteChannel originalChannel) {
            this.originalChannel = originalChannel;
        }

        @Override
        public void close() throws IOException {
            originalChannel.close();
        }

        @Override
        public boolean isOpen() {
            return originalChannel.isOpen();
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            int bytes = originalChannel.write(src);
            incrementOutgoingCounter(bytes);
            return bytes;
        }
    }
}
