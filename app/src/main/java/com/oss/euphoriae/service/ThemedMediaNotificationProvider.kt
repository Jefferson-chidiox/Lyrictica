package com.oss.euphoriae.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.oss.euphoriae.R

@OptIn(UnstableApi::class)
class ThemedMediaNotificationProvider(
    context: Context,
    private val accentColorProvider: () -> Int
) : DefaultMediaNotificationProvider(context) {

    init {
        setSmallIcon(R.drawable.ic_launcher_monochrome)
    }

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        builder.setColor(accentColorProvider())
        builder.setColorized(true)
        return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
    }
}
