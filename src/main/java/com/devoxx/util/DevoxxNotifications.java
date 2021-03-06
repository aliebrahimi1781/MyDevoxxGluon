/**
 * Copyright (c) 2016, Gluon Software
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse
 *    or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.devoxx.util;

import com.devoxx.model.Service;
import com.devoxx.model.Session;
import com.devoxx.model.Vote;
import com.devoxx.views.SessionPresenter;
import com.devoxx.views.dialog.VoteDialog;
import com.gluonhq.charm.down.Services;
import com.gluonhq.charm.down.plugins.LocalNotificationsService;
import com.gluonhq.charm.down.plugins.Notification;
import com.devoxx.DevoxxView;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.devoxx.util.DevoxxLogging.LOGGING_ENABLED;
import java.time.ZonedDateTime;
import static java.time.temporal.ChronoUnit.SECONDS;

@Singleton
public class DevoxxNotifications {
    
    private static final Logger LOG = Logger.getLogger(DevoxxNotifications.class.getName());
    
    public static final String ID_START = "START_";
    public static final String ID_VOTE = "VOTE_";

    private final static String TITLE_VOTE_SESSION = DevoxxBundle.getString("OTN.VISUALS.VOTE_NOW");
    private final static String TITLE_SESSION_STARTS = DevoxxBundle.getString("OTN.VISUALS.SESSION_STARTING_SOON");
    private final static int SHOW_VOTE_NOTIFICATION = -2; // show vote notification two minutes before session ends
    private final static int SHOW_SESSION_START_NOTIFICATION = -15; // show session start warning 15 minutes before posted start time
    
    private final Map<String, Notification> scheduledSessionNotificationMap = new HashMap<>();
    private final Map<String, Notification> voteSessionNotificationMap = new HashMap<>();
    private Notification notification;
        
    @Inject
    private Service service;
    
    private final Optional<LocalNotificationsService> notificationsService;
    
    public DevoxxNotifications() {
        notificationsService = Services.get(LocalNotificationsService.class);
    }

    /**
     * For a given Scheduled Session, it creates two notifications:
     * - One notifification will be triggered by the device before the session starts
     * - One notifification will be triggered by the device right before the session ends
     * 
     * If session was already scheduled, we don't want to schedule it again on the device. 
     * If it wasn't scheduled, we only create notifications for future events.
     * 
     * @param session The Scheduled session
     * @param scheduled if true, we add the notification to the map, so in case it is
     *  delivered, its runnable is available, but we don't need to schedule it again. 
     *  If false, we create the notification and schedule it on the device, if the session is in
     *  the future. 
     */
    public final void addScheduledSessionNotifications(Session session, boolean scheduled) {
        
        ZonedDateTime dateTimeStart = null;
        if (!scheduled) { // otherwise it won't be scheduled
            // Add notification 15 min before session starts
            dateTimeStart = session.getStartDate().plusMinutes(SHOW_SESSION_START_NOTIFICATION);
            if (DevoxxSettings.NOTIFICATION_TESTS) {
                dateTimeStart = dateTimeStart.minus(DevoxxSettings.NOTIFICATION_OFFSET, SECONDS);
            }
        }
        // don't create new notifications for already started sessions
        if (dateTimeStart == null || dateTimeStart.isAfter(ZonedDateTime.now())) {

            notification = new Notification(
                    ID_START + session.getSlotId(),
                    TITLE_SESSION_STARTS, 
                    DevoxxBundle.getString("OTN.VISUALS.IS_ABOUT_TO_START", session.getTitle()),
                    DevoxxNotifications.class.getResourceAsStream("/icon.png"),
                    dateTimeStart,
                    () -> {
                        DevoxxView.SESSION.switchView().ifPresent(presenter -> {
                            SessionPresenter sessionPresenter = (SessionPresenter) presenter;
                            sessionPresenter.showSession(session);
                        });
                    });
            scheduledSessionNotificationMap.put(session.getSlotId(), notification);
            notificationsService.ifPresent(n -> n.getNotifications().add(notification));
        }
        
        ZonedDateTime dateTimeVote = null;
        if (!scheduled) { // otherwise it won't be scheduled
            // Add notification 2 min before session ends
            dateTimeVote = session.getEndDate().plusMinutes(SHOW_VOTE_NOTIFICATION);
            if (DevoxxSettings.NOTIFICATION_TESTS) {
                dateTimeVote = dateTimeVote.minus(DevoxxSettings.NOTIFICATION_OFFSET, SECONDS);
            }
        }
        
        // don't create notifications for already finished sessions
        if (dateTimeVote == null || dateTimeVote.isAfter(ZonedDateTime.now())) {
        
            notification = new Notification(
                    ID_VOTE + session.getSlotId(),
                    TITLE_VOTE_SESSION, 
                    DevoxxBundle.getString("OTN.VISUALS.CAST_YOUR_VOTE_ON", session.getTitle()),
                    DevoxxNotifications.class.getResourceAsStream("/icon.png"),
                    dateTimeVote,
                    () -> {
                        // first go to the session
                        DevoxxView.SESSION.switchView().ifPresent(presenter -> {
                            SessionPresenter sessionPresenter = (SessionPresenter) presenter;
                            sessionPresenter.showSession(session);

                            new Thread(() -> {
                                try {
                                    Thread.sleep(300);
                                } catch (InterruptedException ex) {
                                    // do nothing
                                } finally {
                                    Platform.runLater(() -> {
                                        // then launch vote dialog
                                        VoteDialog dialog = new VoteDialog(session.getTitle());
                                        // New vote
                                        dialog.setVote(new Vote(session.getSlotId()));
                                        dialog.showAndWait()
                                              .ifPresent(voteResult -> service.retrieveVotes().add(voteResult));
                                    });
                                }
                            }).start();
                        });
                    });
            voteSessionNotificationMap.put(session.getSlotId(), notification);
            notificationsService.ifPresent(n -> n.getNotifications().add(notification));
        }
    }
    
    /**
     * For a given Scheduled session, if the user unschedules it, its two notifications
     * will be cancelled on the device
     * @param session 
     */
    public final void removeScheduledSessionNotifications(Session session) {
        /**
        * Remove notification
        */
       notification = scheduledSessionNotificationMap.remove(session.getSlotId());
       if (notification != null) { 
           notificationsService.ifPresent(n -> n.getNotifications().remove(notification));
       }
       notification = voteSessionNotificationMap.remove(session.getSlotId());
       if (notification != null) { 
           notificationsService.ifPresent(n -> n.getNotifications().remove(notification));
       }
    }
    
    private ListChangeListener<Session> scheduledSessionslistener;
    
    /**
     * Called when the application starts, allows retrieving the scheduled
     * notifications, and restoring the notifications map
     */
    public void preloadScheduledSessions() {
        // retrieve schedules sessions to restore notifications
        if (service.isAuthenticated()) { 
            scheduledSessionslistener = (ListChangeListener.Change<? extends Session> c) -> {
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (Session session : c.getAddedSubList()) {
                            if (LOGGING_ENABLED) {
                                LOG.log(Level.INFO, "Adding notification #" + session.getSlotId());
                            }
                            addScheduledSessionNotifications(session, true);
                        }
                    }
                }
            };
            // recreate notifications map
            service.retrieveScheduledSessions().addListener(scheduledSessionslistener);
        }
    }
    
    /**
     * Called when the application has started, to stop preloading the scheduled sessions.
     * At this point, we have all the notifications available.
     */
    public void stopPreloadingScheduledSessions() {
        if (scheduledSessionslistener != null) {
            service.retrieveScheduledSessions().removeListener(scheduledSessionslistener);
            scheduledSessionslistener = null;
        }
    }
    
}
