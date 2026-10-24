package pl.perier.sirion.data

import android.service.notification.NotificationListenerService

/**
 * Does nothing itself. Android lets an app read other apps' media sessions
 * only while one of its notification listeners is allowed, so this is what
 * the root helper allows; [CarPlayMusic] then reads DiPlay's CarPlay session.
 */
class SessionAccess : NotificationListenerService()
