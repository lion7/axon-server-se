/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.localstorage.file;



import io.axoniq.axonserver.localstorage.EventTypeContext;
import io.axoniq.axonserver.localstorage.StorageCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Thread responsible to close the segment when it got full. Also confirms to writer when transaction blocks are close.
 * One instance per event-type (Event,Snapshot) per context
 * @author Zoltan Altfatter
 */

public class Synchronizer {
    private final Logger log = LoggerFactory.getLogger(Synchronizer.class);
    private final SortedMap<WritePosition, StorageCallback> writePositions = new ConcurrentSkipListMap<>();

    private final ScheduledExecutorService fsync;
    private final EventTypeContext context;
    private final StorageProperties storageProperties;
    private final Consumer<WritePosition> completeSegmentCallback;
    private volatile WritePosition current;
    private final ConcurrentSkipListSet<WritePosition> syncAndCloseFile = new ConcurrentSkipListSet<>();
    private volatile ScheduledFuture<?> forceJob;
    private volatile ScheduledFuture<?> syncJob;

    public Synchronizer(EventTypeContext context, StorageProperties storageProperties, Consumer<WritePosition> completeSegmentCallback) {
        this.context = context;
        this.storageProperties = storageProperties;
        this.completeSegmentCallback = completeSegmentCallback;
        fsync = Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory(context + "-synchronizer-"));
    }

    public void notifyWritePositions() {
        try {
                    for (Iterator<Map.Entry<WritePosition, StorageCallback>> iterator = writePositions
                            .entrySet().iterator(); iterator.hasNext(); ) {
                        Map.Entry<WritePosition, StorageCallback> writePositionEntry = iterator
                                .next();

                        WritePosition writePosition = writePositionEntry.getKey();
                        if (writePosition.isComplete() &&
                                writePositionEntry.getValue().onCompleted(writePosition.sequence)) {


                                if (canSyncAt(writePosition)) {
                                    syncAndCloseFile.add(current);
                                }
                                current = writePosition;
                                iterator.remove();
                        } else {
                            break;
                        }
                    }
        } catch (RuntimeException t) {
            writePositions.entrySet().iterator().forEachRemaining(e -> e.getValue().onError(t));
            log.error("Caught exception in the synchronizer for {}", context, t);
        }
    }

    private void syncAndCloseFile() {
        WritePosition toSync = syncAndCloseFile.pollFirst();
        if (toSync != null) {
            try {
                log.debug("Syncing segment and index at {}", toSync);
                completeSegmentCallback.accept(toSync);
                log.info("Synced segment and index at {}", toSync);
            } catch (Exception ex) {
                log.warn("Failed to close file {} - {}", toSync.segment, ex.getMessage());
                syncAndCloseFile.add(toSync);
            }
        }
    }

    public void register(WritePosition writePosition, StorageCallback callback) {
        writePositions.put(writePosition, callback);
    }


    private boolean canSyncAt(WritePosition writePosition) {
        if( current != null && current.segment != writePosition.segment) {
            log.debug("can sync at {}: {}", writePosition.segment, current.segment);
        }
        return current != null && current.segment != writePosition.segment;
    }

    public synchronized void init(WritePosition writePosition) {
        current = writePosition;
        log.debug("Initializing at {}", writePosition);
        if( syncJob == null) {
            syncJob = fsync.scheduleWithFixedDelay(this::syncAndCloseFile, storageProperties.getSyncInterval(), storageProperties.getSyncInterval(), TimeUnit.MILLISECONDS);
            log.debug("Scheduled syncJob");
        }
        if( forceJob == null) {
            forceJob = fsync.scheduleWithFixedDelay(this::forceCurrent, storageProperties.getForceInterval(), storageProperties.getForceInterval(), TimeUnit.MILLISECONDS);
            log.debug("Scheduled forceJob");
        }
    }

    public void forceCurrent() {
        if( current != null) {
            current.force();
        }
    }

    public void shutdown(boolean shutdown) {
        if( syncJob != null) syncJob.cancel(false);
        if( forceJob != null) forceJob.cancel(false);
        syncJob = null;
        forceJob = null;
        while( ! syncAndCloseFile.isEmpty()) {
            syncAndCloseFile();
        }
        if( shutdown) fsync.shutdown();
    }

}
