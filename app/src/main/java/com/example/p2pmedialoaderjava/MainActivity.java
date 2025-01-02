package com.example.p2pmedialoaderjava;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;

import com.novage.p2pml.P2PMediaLoader;

@UnstableApi
public class MainActivity extends AppCompatActivity {
    private static final String MANIFEST_URL = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8";
    private static final int SERVER_PORT = 8081;
    private static final String CORE_CONFIG_JSON = "{\"swarmId\":\"TEST_KOTLIN\"}";

    private ExoPlayer exoPlayer;
    private P2PMediaLoader p2pMediaLoader;

    private PlayerView playerView;
    private ProgressBar loadingIndicator;
    private TextView videoTitle;

    private void setupPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();

        String manifestUrl = p2pMediaLoader.getManifestUrl(MANIFEST_URL);

        MediaItem mediaItem = MediaItem.fromUri(manifestUrl);
        HlsMediaSource mediaSource = new HlsMediaSource.Factory(
                new DefaultHttpDataSource.Factory()
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000)
        ).createMediaSource(mediaItem);

        playerView.setPlayer(exoPlayer);

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState != Player.STATE_READY) return;

                loadingIndicator.setVisibility(View.GONE);
            }
        });

        p2pMediaLoader.attachPlayer(exoPlayer);
    }

    private void onError(String error) {
        // Handle error
        Log.d("Error while initializing P2P Media Loader", error);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializeP2PMediaLoader() {
        p2pMediaLoader = new P2PMediaLoader(
                this::setupPlayer,
                this::onError,
                SERVER_PORT,
                CORE_CONFIG_JSON
        );

        p2pMediaLoader.start(this);
    }

    private void initializeUI() {
        playerView = findViewById(R.id.playerView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        videoTitle = findViewById(R.id.videoTitle);

        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBarsInsets.left, systemBarsInsets.top,
                    systemBarsInsets.right, systemBarsInsets.bottom);
            return insets;
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initializeUI();
        initializeP2PMediaLoader();

        applyWindowInsets();
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private void stopP2PMediaLoader() {
        if (p2pMediaLoader != null) {
            p2pMediaLoader.stop();
            p2pMediaLoader = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (p2pMediaLoader != null) {
            p2pMediaLoader.applyDynamicConfig("{ \"isP2PDisabled\": false }");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (p2pMediaLoader != null) {
            p2pMediaLoader.applyDynamicConfig("{ \"isP2PDisabled\": true }");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        stopP2PMediaLoader();
    }
}