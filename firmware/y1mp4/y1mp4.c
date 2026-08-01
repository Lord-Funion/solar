/***************************************************************************
 * Y1 MP4 viewer bridge
 *
 * This is a Rockbox viewer plugin for the Innioasis Y1. Rockbox's bundled
 * mpegplayer only understands MPEG program streams and MPEG-1/2 video; this
 * plugin hands MP4/M4V/MOV files to Android's registered video activity so
 * the Y1 can use its platform H.264/AAC decoder.
 *
 * Copyright (C) 2026 Lord Funion
 * SPDX-License-Identifier: GPL-2.0-or-later
 ****************************************************************************/

#include "plugin.h"

static bool supported_extension(const char *path)
{
    const char *ext = rb->strrchr(path, '.');

    return ext != NULL &&
           (rb->strcasecmp(ext, ".mp4") == 0 ||
            rb->strcasecmp(ext, ".m4v") == 0 ||
            rb->strcasecmp(ext, ".mov") == 0);
}

static const char *base_name(const char *path)
{
    const char *slash = rb->strrchr(path, '/');
    return slash == NULL ? path : slash + 1;
}

enum plugin_status plugin_start(const void *parameter)
{
    const char *path = (const char *)parameter;
    int result;

    if (path == NULL || path[0] == '\0')
    {
        rb->splashf(HZ * 4, "Open an MP4 file from Rockbox Files");
        return PLUGIN_OK;
    }

    if (!supported_extension(path))
    {
        rb->splashf(HZ * 3, "Y1 MP4 supports .mp4, .m4v and .mov");
        return PLUGIN_ERROR;
    }

    /* Release Rockbox's audio path before Android requests media focus. */
    if (rb->audio_status() != 0)
        rb->audio_stop();

    rb->splashf(HZ, "Opening %s", base_name(path));
    result = rb->y1_video_play(path);
    if (result != 0)
    {
        rb->splashf(HZ * 4, "Android MP4 launch failed (%d)", result);
        return PLUGIN_ERROR;
    }

    return PLUGIN_OK;
}
