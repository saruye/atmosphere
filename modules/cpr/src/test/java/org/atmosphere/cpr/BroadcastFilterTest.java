/*
 * Copyright 2012 Jean-Francois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.cpr;

import org.atmosphere.cache.AbstractBroadcasterCache;
import org.atmosphere.cache.HeaderBroadcasterCache;
import org.atmosphere.client.TrackMessageSizeFilter;
import org.atmosphere.container.BlockingIOCometSupport;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRACKMESSAGESIZE;
import static org.atmosphere.cpr.HeaderConfig.X_CACHE_DATE;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

public class BroadcastFilterTest {

    private AtmosphereResource ar;
    private Broadcaster broadcaster;
    private AR atmosphereHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        AtmosphereConfig config = new AtmosphereFramework().getAtmosphereConfig();
        DefaultBroadcasterFactory factory = new DefaultBroadcasterFactory(DefaultBroadcaster.class, "NEVER", config);
        broadcaster = factory.get(DefaultBroadcaster.class, "test");
        atmosphereHandler = new AR();
        ar = new AtmosphereResourceImpl(config,
                broadcaster,
                mock(AtmosphereRequest.class),
                AtmosphereResponse.create(),
                mock(BlockingIOCometSupport.class),
                atmosphereHandler);

        broadcaster.addAtmosphereResource(ar);
    }

    @Test
    public void testProgrammaticBroadcastFilter() throws ExecutionException, InterruptedException, ServletException {
        broadcaster.getBroadcasterConfig().addFilter(new Filter());
        broadcaster.broadcast("0").get();

        assertEquals(atmosphereHandler.value.get().toString(), "0foo");
    }

    @Test
    public void testInitBroadcastFilter() throws ExecutionException, InterruptedException, ServletException {
        AtmosphereConfig config = new AtmosphereFramework()
                .addInitParameter(ApplicationConfig.BROADCAST_FILTER_CLASSES, Filter.class.getName())
                .setAsyncSupport(mock(BlockingIOCometSupport.class))
                .init(new ServletConfig() {
                    @Override
                    public String getServletName() {
                        return "void";
                    }

                    @Override
                    public ServletContext getServletContext() {
                        return mock(ServletContext.class);
                    }

                    @Override
                    public String getInitParameter(String name) {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getInitParameterNames() {
                        return null;
                    }
                })
                .getAtmosphereConfig();

        DefaultBroadcasterFactory factory = new DefaultBroadcasterFactory(DefaultBroadcaster.class, "NEVER", config);
        broadcaster = factory.get(DefaultBroadcaster.class, "test");
        atmosphereHandler = new AR();
        ar = new AtmosphereResourceImpl(config,
                broadcaster,
                mock(AtmosphereRequest.class),
                AtmosphereResponse.create(),
                mock(BlockingIOCometSupport.class),
                atmosphereHandler);

        broadcaster.addAtmosphereResource(ar);

        broadcaster.broadcast("0").get();

        assertEquals(atmosphereHandler.value.get().toString(), "0foo");
    }

    @Test
    public void testMultipleFilter() throws ExecutionException, InterruptedException {

        broadcaster.getBroadcasterConfig().addFilter(new Filter("1"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("2"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("3"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("4"));

        broadcaster.broadcast("0").get();

        assertEquals(atmosphereHandler.value.get().toString(), "01234");
    }

    @Test
    public void testMultiplePerRequestFilter() throws ExecutionException, InterruptedException {

        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("1"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("2"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("3"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("4"));

        broadcaster.broadcast("0").get();

        assertEquals(atmosphereHandler.value.get().toString(), "01234");
    }

    @Test
    public void testMultipleMixedFilter() throws ExecutionException, InterruptedException {
        broadcaster.getBroadcasterConfig().addFilter(new Filter("1"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("2"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("3"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("4"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("1"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("2"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("3"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("4"));

        broadcaster.broadcast("0").get();
        assertEquals(atmosphereHandler.value.get().toString(), "01234");
    }

    @Test
    public void testMultipleMixedPerRequestFilter() throws ExecutionException, InterruptedException {
        broadcaster.getBroadcasterConfig().addFilter(new Filter("1"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("a"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("2"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("b"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("3"));
        broadcaster.getBroadcasterConfig().addFilter(new PerRequestFilter("c"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("4"));

        broadcaster.broadcast("0").get();
        assertEquals(atmosphereHandler.value.get().toString(), "0abc");
    }

    @Test
    public void testMixedPerRequestFilter() throws ExecutionException, InterruptedException {
        broadcaster.getBroadcasterConfig().addFilter(new Filter("1"));
        broadcaster.getBroadcasterConfig().addFilter(new DoNohingFilter("a"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("2"));
        broadcaster.getBroadcasterConfig().addFilter(new DoNohingFilter("b"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("3"));
        broadcaster.getBroadcasterConfig().addFilter(new DoNohingFilter("c"));
        broadcaster.getBroadcasterConfig().addFilter(new Filter("4"));

        broadcaster.broadcast("0").get();
        assertEquals(atmosphereHandler.value.get().toString(), "01a2b3c4");
    }

    private final static class PerRequestFilter implements PerRequestBroadcastFilter {

        String msg;

        public PerRequestFilter(String msg) {
            this.msg = msg;
        }

        @Override
        public BroadcastAction filter(Object originalMessage, Object message) {
            return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, message + msg);
        }

        @Override
        public BroadcastAction filter(AtmosphereResource atmosphereResource, Object originalMessage, Object message) {
            return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, message + msg);
        }
    }

    private final static class DoNohingFilter implements PerRequestBroadcastFilter {

        String msg;

        public DoNohingFilter(String msg) {
            this.msg = msg;
        }

        @Override
        public BroadcastAction filter(Object originalMessage, Object message) {
            return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, message + msg);
        }

        @Override
        public BroadcastAction filter(AtmosphereResource atmosphereResource, Object originalMessage, Object message) {
            return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, originalMessage);
        }
    }

    private final static class Filter implements BroadcastFilter {

        final String msg;

        public Filter() {
            this.msg = "foo";
        }

        public Filter(String msg) {
            this.msg = msg;
        }

        @Override
        public BroadcastAction filter(Object originalMessage, Object message) {
            return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, message + msg);
        }
    }

    public final static class AR implements AtmosphereHandler {

        public AtomicReference<StringBuffer> value = new AtomicReference<StringBuffer>(new StringBuffer());

        @Override
        public void onRequest(AtmosphereResource e) throws IOException {
        }

        @Override
        public void onStateChange(AtmosphereResourceEvent e) throws IOException {
            if (e.getMessage() instanceof List) {
                value.get().append(((List) e.getMessage()).get(0));
            } else {
                value.get().append(e.getMessage());
            }
        }

        @Override
        public void destroy() {
        }
    }

    @Test
    public void testTrackMessageSizeFilter() throws ExecutionException, InterruptedException {
        //Make sure we are empty.
        broadcaster.removeAtmosphereResource(ar);
        broadcaster.getBroadcasterConfig().setBroadcasterCache(new AbstractBroadcasterCache() {
            @Override
            public void addToCache(String broadcasterId, AtmosphereResource r, Message e) {

                long now = System.nanoTime() * 2;
                put(e, now);
            }

            @Override
            public List<Object> retrieveFromCache(String id, AtmosphereResource r) {
                long cacheHeaderTime = Long.valueOf(System.nanoTime());
                return get(cacheHeaderTime);
            }
        }).addFilter(new TrackMessageSizeFilter() {
            @Override
            public BroadcastAction filter(AtmosphereResource r, Object message, Object originalMessage) {

                String msg = message.toString();
                msg = msg.length() + "|" + msg;
                return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, msg);
            }
        });

        broadcaster.broadcast("0").get();
        broadcaster.addAtmosphereResource(ar);
        assertEquals(atmosphereHandler.value.get().toString(), "1|0");
    }

    @Test
    public void testComplexTrackMessageSizeFilter() throws ExecutionException, InterruptedException {
        //Make sure we are empty.
        broadcaster.removeAtmosphereResource(ar);
        broadcaster.getBroadcasterConfig().setBroadcasterCache(new AbstractBroadcasterCache() {
            @Override
            public void addToCache(String broadcasterId, AtmosphereResource r, Message e) {

                long now = System.nanoTime() * 2;
                put(e, now);
            }

            @Override
            public List<Object> retrieveFromCache(String id, AtmosphereResource r) {
                long cacheHeaderTime = Long.valueOf(System.nanoTime());
                return get(cacheHeaderTime);
            }
        }).addFilter(new TrackMessageSizeFilter() {
            @Override
            public BroadcastAction filter(AtmosphereResource r, Object message, Object originalMessage) {

                String msg = message.toString();
                msg = msg.length() + "|" + msg;
                return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, msg);
            }
        });
        broadcaster.broadcast("0").get();
        broadcaster.addAtmosphereResource(ar);

        broadcaster.broadcast("XXX").get();
        broadcaster.removeAtmosphereResource(ar);
        broadcaster.addAtmosphereResource(ar);
        assertEquals(atmosphereHandler.value.get().toString(), "1|03|XXX1|0");
    }
}
