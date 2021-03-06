package com.king.player.exoplayer;


import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.king.player.kingplayer.KingPlayer;
import com.king.player.kingplayer.source.DataSource;
import com.king.player.kingplayer.util.LogUtils;

import java.util.Map;

/**
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 */
public class ExoPlayer extends KingPlayer<SimpleExoPlayer> {

    private SimpleExoPlayer mMediaPlayer;

    private Context mContext;

    private DataSource mDataSource;

    private Bundle mBundle = obtainBundle();

    public ExoPlayer(@NonNull Context context){
        this(context,null);
    }

    public ExoPlayer(@NonNull Context context,@Nullable SimpleExoPlayer mediaPlayer){
        this.mContext = context.getApplicationContext();
        init(mediaPlayer);
    }


    private void init(SimpleExoPlayer mediaPlayer){
        if(mediaPlayer == null){
            RenderersFactory renderersFactory = new DefaultRenderersFactory(mContext);
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(mContext);
            mMediaPlayer = new SimpleExoPlayer.Builder(mContext,renderersFactory).setTrackSelector(trackSelector).build();
        }else{
            mMediaPlayer = mediaPlayer;
        }
        addListener();
    }


    @Override
    public void setDataSource(@NonNull DataSource dataSource) {
        //TODO

        try{
            MediaSource mediaSource = obtainMediaSource(dataSource);
            if(mediaSource != null){
                mDataSource = dataSource;
                mMediaPlayer.setMediaSource(obtainMediaSource(dataSource));

                mMediaPlayer.prepare();

                mCurrentState = STATE_PREPARED;
                mTargetState = STATE_PREPARING;
                sendPlayerEvent(Event.EVENT_ON_DATA_SOURCE_SET);
            }else{
                LogUtils.w("mediaSource = null");
            }
        }catch (Exception e){
            handleException(e,false);
            mCurrentState = STATE_ERROR;
            sendErrorEvent(ErrorEvent.ERROR_EVENT_COMMON);
        }

    }

    private MediaSource obtainMediaSource(@NonNull DataSource dataSource){
        Uri videoUri = null;
        if(!TextUtils.isEmpty(dataSource.getPath())){
            videoUri = Uri.parse(dataSource.getPath());
        }else if(dataSource.getUri() != null){
            videoUri = dataSource.getUri();
        }else if(!TextUtils.isEmpty(dataSource.getAssetFilePath())){
            try {
                AssetDataSource assetDataSource = new AssetDataSource(mContext);
                assetDataSource.open(new DataSpec(DataSource.buildAssetsUri(dataSource.getAssetFilePath())));
                videoUri = assetDataSource.getUri();
            }catch (Exception e){
                handleException(e,false);
            }
        }

        MediaSource mediaSource = null;

        if(videoUri != null){
            //if scheme is http or https and DataSource contain extra data, use DefaultHttpDataSourceFactory.
            String scheme = videoUri.getScheme();
            Map<String, String> extra = dataSource.getHeaders();
            //setting user-agent from extra data
            String settingUserAgent = extra!=null ? extra.get("User-Agent"):"";
            //if not setting, use default user-agent
            String userAgent = !TextUtils.isEmpty(settingUserAgent) ? settingUserAgent : Util.getUserAgent(mContext, mContext.getPackageName());

            int contentType = Util.inferContentType(videoUri);

            com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(mContext,
                            userAgent, new DefaultBandwidthMeter.Builder(mContext).build());

            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(videoUri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build();
            switch (contentType){
                case C.TYPE_DASH:
                    mediaSource = new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                    break;
                case C.TYPE_HLS:
                    mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                    break;
                case C.TYPE_SS:
                    mediaSource = new SsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                    break;
                case C.TYPE_OTHER:
                default:
                    mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                    break;
            }
        }

        return mediaSource;
    }

    private void addListener(){
        if(available()){
            mMediaPlayer.addVideoListener(mVideoListener);
            mMediaPlayer.addListener(mEventListener);
        }

    }

    private void resetListener(){
        if(available()){
            mMediaPlayer.removeVideoListener(mVideoListener);
            mMediaPlayer.removeListener(mEventListener);
        }
    }

    private void recycleBundle(){
        if(mBundle != null){
            mBundle.clear();
        }
    }


    private VideoListener mVideoListener = new VideoListener() {
        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            sendVideoSizeChangeEvent(width,height);
        }

        @Override
        public void onSurfaceSizeChanged(int width, int height) {
            LogUtils.d(String.format("onSurfaceSizeChanged: %d*%d",width,height));
        }

        @Override
        public void onRenderedFirstFrame() {
            sendPlayerEvent(Event.EVENT_ON_VIDEO_RENDER_START);

        }
    };

    private EventListener mEventListener = new EventListener() {
        @Override
        public void onTimelineChanged(Timeline timeline, int reason) {
            sendPlayerEvent(Event.EVENT_ON_TIMER_UPDATE);
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {

        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }

        @Override
        public void onIsLoadingChanged(boolean isLoading) {
            LogUtils.d("onIsLoadingChanged: " + mMediaPlayer.getBufferedPercentage());
            if(isLoading){
                sendPlayerEvent(Event.EVENT_ON_BUFFERING_START);
            }else{
                sendPlayerEvent(Event.EVENT_ON_BUFFERING_END);
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            switch (state){
                case Player.STATE_READY:
                    LogUtils.d("Player.STATE_READY");
                    break;
                case Player.STATE_BUFFERING:
                    LogUtils.d("Player.STATE_BUFFERING");
                    sendBufferingUpdateEvent((int)getBufferPercentage());
                    break;
                case Player.STATE_ENDED:
                    LogUtils.d("Player.STATE_ENDED");
                    sendPlayerEvent(Event.EVENT_ON_PLAY_COMPLETE);
                    break;
                case Player.STATE_IDLE:
                    LogUtils.d("Player.STATE_IDLE");
                    break;
            }
            mBundle.putInt(EventBundleKey.KEY_ORIGINAL_EVENT,state);
            sendPlayerEvent(Event.EVENT_ON_STATUS_CHANGE,mBundle);
            recycleBundle();
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            LogUtils.d("onPlayWhenReadyChanged: " + playWhenReady);
        }

        @Override
        public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
            LogUtils.d("onPlaybackSuppressionReasonChanged: " + playbackSuppressionReason);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            LogUtils.d("onIsPlayingChanged: " + isPlaying);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            LogUtils.d("onRepeatModeChanged: " + repeatMode);
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            LogUtils.d("onShuffleModeEnabledChanged: " + shuffleModeEnabled);
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            handleException(error,false);
            int event = ErrorEvent.ERROR_EVENT_COMMON;
            switch (error.type){
                case ExoPlaybackException.TYPE_OUT_OF_MEMORY:
                    LogUtils.w("ExoPlaybackException.TYPE_OUT_OF_MEMORY");
                    break;
                case ExoPlaybackException.TYPE_REMOTE:
                    LogUtils.w("ExoPlaybackException.TYPE_REMOTE");
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    LogUtils.w("ExoPlaybackException.TYPE_RENDERER");
                    break;
                case ExoPlaybackException.TYPE_SOURCE:
                    LogUtils.w("ExoPlaybackException.TYPE_SOURCE");
                    event = ErrorEvent.ERROR_EVENT_IO;
                    break;
                case ExoPlaybackException.TYPE_TIMEOUT:
                    LogUtils.w("ExoPlaybackException.TYPE_TIMEOUT");
                    event = ErrorEvent.ERROR_EVENT_TIMED_OUT;
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    LogUtils.w("ExoPlaybackException.TYPE_UNEXPECTED");
                    event = ErrorEvent.ERROR_EVENT_UNKNOWN;
                    break;
            }
            mBundle.putInt(EventBundleKey.KEY_ORIGINAL_EVENT,error.type);
            sendErrorEvent(event,mBundle);
            recycleBundle();
        }

        @Override
        public void onPositionDiscontinuity(int reason) {
            LogUtils.d("onPositionDiscontinuity: " + reason);
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            LogUtils.d("onPositionDiscontinuity: " + playbackParameters);
        }

        @Override
        public void onExperimentalOffloadSchedulingEnabledChanged(boolean offloadSchedulingEnabled) {
            LogUtils.d("onExperimentalOffloadSchedulingEnabledChanged: " + offloadSchedulingEnabled);
        }
    };


    private boolean available(){
        return mMediaPlayer != null;
    }

    private boolean hasDataSource(){
        return available() && mDataSource != null;
    }


    @Override
    public void start() {
        try{
            if(hasDataSource() && (mCurrentState == STATE_PREPARED
                    || mCurrentState == STATE_PAUSED
                    || mCurrentState == STATE_PLAYBACK_COMPLETED)){
                mMediaPlayer.play();
                mCurrentState = STATE_PLAYING;
                LogUtils.d("start");
                sendPlayerEvent(Event.EVENT_ON_START);

            }else{
                LogUtils.d("currentState = " + mCurrentState);
            }
        }catch (Exception e){
            handleException(e,true);
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        try{
            if(hasDataSource() && (mCurrentState == STATE_PREPARED
                    || mCurrentState == STATE_PLAYING
                    || mCurrentState == STATE_PLAYBACK_COMPLETED)){
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
                sendPlayerEvent(Event.EVENT_ON_PAUSE);
                LogUtils.d("pause");
            }else{
                LogUtils.d("currentState = " + mCurrentState);
            }
        }catch (Exception e){
            handleException(e,true);
        }
        mTargetState = STATE_PAUSED;
    }

    @Override
    public void stop() {
        try{
            if(hasDataSource() && (mCurrentState == STATE_PREPARED
                    || mCurrentState == STATE_PLAYING
                    || mCurrentState == STATE_PAUSED
                    || mCurrentState == STATE_PLAYBACK_COMPLETED)){
                mMediaPlayer.stop();
                mCurrentState = STATE_STOPPED;
                sendPlayerEvent(Event.EVENT_ON_STOP);
                LogUtils.d("stop");
            }else{
                LogUtils.d("currentState = " + mCurrentState);
            }
        }catch (Exception e){
            handleException(e,true);
        }
        mTargetState = STATE_STOPPED;
    }

    @Override
    public void release() {
        if(available()){
            mMediaPlayer.release();
            mCurrentState = STATE_IDLE;
            sendPlayerEvent(Event.EVENT_ON_RELEASE);
            LogUtils.d("release");
        }
        resetListener();
        mTargetState = STATE_IDLE;
    }

    @Override
    public void reset() {
        if(available()){
            mMediaPlayer.stop(true);
            mCurrentState = STATE_IDLE;
            sendPlayerEvent(Event.EVENT_ON_RESET);
            LogUtils.d("reset");
        }
        mTargetState = STATE_IDLE;
    }

    @Override
    public boolean isPlaying() {
        try {
            if(available()){
                return mMediaPlayer.isPlaying();
            }
        }catch (Exception e){
            handleException(e,false);
        }
        return false;
    }

    @Override
    public void setVolume(float volume) {
        if(available()){
            mMediaPlayer.setVolume(volume);
        }
    }

    @Override
    public void seekTo(int msec) {
        try{
            if(available() && (mCurrentState == STATE_PREPARED
                    || mCurrentState == STATE_PAUSED
                    || mCurrentState == STATE_PLAYBACK_COMPLETED)){
                mMediaPlayer.seekTo(msec);
                Bundle bundle = obtainBundle();
                bundle.putInt(EventBundleKey.KEY_TIME,msec);
                sendPlayerEvent(Event.EVENT_ON_SEEK_TO,bundle);
            }
        }catch (Exception e){
            handleException(e,true);
        }
    }

    @Override
    public int getCurrentPosition() {
        if(!available()){
            return -1;
        }
        return (int)mMediaPlayer.getCurrentPosition();
    }

    @Override
    public int getDuration() {
        if(!available()){
            return -1;
        }
        return (int) mMediaPlayer.getDuration();
    }

    @Override
    public float getBufferPercentage() {
        if(available()){
            return mMediaPlayer.getBufferedPercentage();
        }
        return super.getBufferPercentage();
    }

    @Override
    public void setSpeed(float speed) {
        try{
            if (available() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParameters parameters = new PlaybackParameters(speed, 1f);
                mMediaPlayer.setPlaybackParameters(parameters);

            }
        }catch (Exception e){
            handleException(e,false);
        }
    }

    @Override
    public float getSpeed() {
        try{
            if(available()) {
                return mMediaPlayer.getPlaybackParameters().speed;
            }
        }catch (Exception e){
            handleException(e,false);
        }
        return 1.0f;
    }

    @Override
    public void setLooping(boolean looping) {
        setLooping(looping);
        if(available()){
            mMediaPlayer.setRepeatMode(looping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        }

    }

    @Override
    public SimpleExoPlayer getPlayer() {
        return mMediaPlayer;
    }

    @Override
    public void setSurface(@NonNull SurfaceHolder surfaceHolder) {
        mMediaPlayer.setVideoSurfaceHolder(surfaceHolder);
        sendPlayerEvent(Event.EVENT_ON_SURFACE_HOLDER_UPDATE);
    }

    @Override
    public void setSurface(@NonNull Surface surface) {
        mMediaPlayer.setVideoSurface(surface);
        sendPlayerEvent(Event.EVENT_ON_SURFACE_UPDATE);
    }

    @Override
    public void setSurface(@NonNull SurfaceTexture surfaceTexture) {
        setSurface(new Surface(surfaceTexture));

    }

    @Override
    public void updateSurface(int width, int height) {

    }

    @Override
    public void surfaceDestroy() {

    }
}
