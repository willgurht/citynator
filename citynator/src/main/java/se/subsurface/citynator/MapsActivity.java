package se.subsurface.citynator;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.plattysoft.leonids.ParticleSystem;

import de.greenrobot.event.EventBus;
import se.subsurface.citynator.Model.FlagMatch;
import se.subsurface.citynator.Model.GameType;
import se.subsurface.citynator.Model.Place;
import se.subsurface.citynator.Model.RoundResult;


public class MapsActivity extends FragmentActivity implements GoogleMap.OnMapClickListener, OnMapReadyCallback, View.OnClickListener {

    private static final String TAG = "MapsActivity";


    private static final String PRE_ROUND = "PreRound";
    private static final String TIMER = "Timer";
    private static final String BOUNDS = "Bounds";
    int indicatorWidth;
    private GameType gameType;
    //Views
    private ImageView mFlagView;
    private ViewGroup mapOverlay;
    private GameTimer mGameTimer;
    private MotionEvent mMotionEvent;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    //    private TextView mCountDown;
    private TextView mCityName;
    private TextView mAdmin;
    private TextView mCountry;
    private int mPadding;
    private View mBtnResults;
    private View mBtnGo;
    private TextView map_hit;
    private LinearLayout map_hit_container;
    private LinearLayout timeIndicator;
    private FlagMatch mMatch;
    private boolean resumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate Enter savedInstanceState=" + savedInstanceState);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //Find view stuff
        mPadding = this.getResources().getDimensionPixelSize(R.dimen.map_marker_padding);
        mapOverlay = (ViewGroup) findViewById(R.id.map_overlay);
        mFlagView = (ImageView) findViewById(R.id.flag);
        //mCountDown = (TextView) findViewById(R.id.count_down);
        mCityName = (TextView) findViewById(R.id.city_name);
        mAdmin = (TextView) findViewById(R.id.admin_name);
        mCountry = (TextView) findViewById(R.id.country);
        map_hit = (TextView) findViewById(R.id.map_hit);
        map_hit_container = (LinearLayout) findViewById(R.id.map_hit_container);

        timeIndicator = (LinearLayout) findViewById(R.id.time_indicator);
        indicatorWidth = timeIndicator.getLayoutParams().width;
        mBtnResults = findViewById(R.id.maps_show_results);
        mBtnGo = findViewById(R.id.maps_btn_go);

        mBtnResults.setOnClickListener(this);
        mBtnGo.setOnClickListener(this);

        if (savedInstanceState != null) {
            boolean preRound = savedInstanceState.getBoolean(PRE_ROUND);
            long timeRemaining = savedInstanceState.getLong(TIMER);
            Log.d(TAG, "preRound=" + preRound + ", timeRemaining=" + timeRemaining);
            mMatch = FlagItApplication.getInstance().match;
            mGameTimer = new GameTimer(timeRemaining, preRound, mMatch.tick_interval_ms);

        } else {
            //this should propably be done in some destruct cycle, instead of startup
            FlagItApplication.getInstance().match = null;
        }

        EventBus.getDefault().register(this);

        //Area is used to create mMatch
        String area = getIntent().getStringExtra("Area");
        gameType = FlagItApplication.getInstance().getGameType(area);
        Log.d(TAG, "onCreate Return");

        setUpMapIfNeeded();
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        FlagItApplication.getInstance().match = null;
        mMatch = null;
        if (mGameTimer != null) {
            mGameTimer.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    private void updateCenterText() {
        if (mGameTimer.preRound && mMatch.currentRound == 0) {
            updateCenterText("Get Ready!", "", "", "");
        } else if ((mGameTimer.preRound && mMatch.currentRound > 0)) {
            updateCenterText("", "", "", "");
        } else {
            Place place = mMatch.places.get(mMatch.currentRound);
            updateCenterText(place.name, place.admin, place.country, place.countryCode);
        }
    }

    private void updateCenterText(String city, String admin, String country, String countryCode) {
        int id = this.getResources().getIdentifier(countryCode, "drawable",
                this.getPackageName());


        mFlagView.setImageResource(id);
        mCityName.setText(city);
        mAdmin.setText(admin);
        mCountry.setText(country);

        //      YoYo.with(Techniques.Pulse).duration(mMatch.count_down_ms/2).playOn(mFlagView);
        YoYo.with(Techniques.Pulse).duration(mMatch.count_down_ms / 2).playOn((ViewGroup) mCityName.getParent());
        //    YoYo.with(Techniques.Pulse).duration(mMatch.count_down_ms/2).playOn(mAdmin);
        //YoYo.with(Techniques.Pulse).duration(mMatch.count_down_ms/2).playOn(mCountry);
    }

    @Override
    public void onMapClick(LatLng click) {
        if (mMatch != null && mMatch.roundInProgress && !mMatch.clicked) {
            mMatch.clicked = true;


            RoundResult result = mMatch.roundCompleted(click, mGameTimer.timeEllapsed);
            int accuracyColor = FlagItUtils.getAccuracyColor(this, result.accuracy);

            String distance = FlagItUtils.round(result.distance, 0);
            map_hit.setVisibility(View.VISIBLE);
            map_hit.setTextColor(accuracyColor);
            map_hit.setText(distance);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) map_hit_container.getLayoutParams();
            params.leftMargin = (int) mMotionEvent.getX() - params.width / 2;

            if (!result.below) {
                params.topMargin = (int) mMotionEvent.getY() - params.height - FlagItUtils.getStatusBarHeight(this);
            } else {
                params.topMargin = (int) mMotionEvent.getY() - FlagItUtils.getStatusBarHeight(this);
            }

            params.addRule(RelativeLayout.CENTER_IN_PARENT, 0);

            map_hit_container.setLayoutParams(params);

            YoYo.with(Techniques.Landing)
                    .duration(3000)
                    .playOn(map_hit);

            FlagItUtils.drawPoly(result, mMap, this, true);
            int particleSize = 0;
            switch (result.accuracy) {
                case RED:
                    particleSize = 100;
                    break;
                case ORANGE:
                    particleSize = 30;
                    break;
                case YELLOW:
                    particleSize = 20;
                    break;
                case BLACK:
                    Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake2);
                    mapOverlay.startAnimation(shake);
                    break;
            }

            //Draw the explosion
            if (mMotionEvent != null) {
                new ParticleSystem(this, particleSize, R.drawable.star_white, 800)
                        .setSpeedRange(0.1f, 0.25f)
                        .emit((int) mMotionEvent.getX(), (int) mMotionEvent.getY(), 1000, 100);
            }
            mGameTimer.cancel();

            nextRound();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        if (mMap != null) {
            resumeMatch();
        }
        SupportMapFragment mapFragment = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
        mapFragment.getMapAsync(this);


    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        googleMap.setOnMapClickListener(this);
        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            public boolean onMarkerClick(Marker marker) {
                //disable marker center click
                return true;
            }
        });
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = mMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.mapstyle));

            if (!success) {
                Log.e("MapsActivityRaw", "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e("MapsActivityRaw", "Can't find style.", e);
        }
        //googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        // To Disable Zoom you can do2 the following.
        googleMap.getUiSettings().setScrollGesturesEnabled(false);
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setZoomGesturesEnabled(false);

        resumeMatch();
    }

    private void resumeMatch() {
        Log.d(TAG, "resumeMatch() resumed=" + resumed + ", mMatch=" + mMatch);
        if (!resumed) {
            resumed = true;

            if (mMatch == null) {
                mBtnGo.setVisibility(View.VISIBLE);
                mBtnResults.setVisibility(View.GONE);
                EventBus.getDefault().post(new EventStartMatch(gameType));
            } else {
                mMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
                    @Override
                    public void onCameraIdle() {
                        // Move camera.
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mMatch.bounds, mPadding));
                        // Remove listener to prevent position reset on camera move.
                        mMap.setOnCameraIdleListener(null);
                    }
                });
                switch (mMatch.matchState) {
                    case NOT_STARTED:
                        mBtnGo.setVisibility(View.VISIBLE);
                        break;
                    case IN_PROGRESS:
                        mBtnGo.setVisibility(View.GONE);
                        mBtnResults.setVisibility(View.GONE);
                        //      setBounds(mMatch.bounds, true);
                        updateCenterText();
                        //only start timer if mMatch was started before
                        mGameTimer.startIfNotStarted();
                        break;
                    case COMPLETED:
                        mBtnResults.setVisibility(View.VISIBLE);
                        mBtnGo.setVisibility(View.GONE);
                        break;
                }
            }
        } else {
            //Trigger the timer here, if it exists. needed when shutdown screen and return on
            if (mGameTimer != null && mMatch.matchState == FlagMatch.MatchState.IN_PROGRESS) {
                mGameTimer.startIfNotStarted();
            }
        }
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mGameTimer != null) {
            mGameTimer.cancel();
            mGameTimer.started = false;
            if (mGameTimer.millisUntilFinished > 0) {
                outState.putBoolean(PRE_ROUND, mGameTimer.preRound);
                outState.putLong(TIMER, mGameTimer.millisUntilFinished);
            }

            Log.d(TAG, "onSaveInstanceState() outstate=" + outState);
        }
        if (mMatch != null) {
            outState.putParcelable(BOUNDS, mMatch.bounds);
        }
    }

    public void onClick(View v) {
        Log.d(TAG, "onClick");
        switch (v.getId()) {
            case R.id.maps_show_results:
                Log.d(TAG, "matchComplete()");
                Intent intent = new Intent(this, MatchResultActivity.class);
                intent.putExtra("FlagMatch", mMatch);
                intent.putExtra("Bounds", mMatch.bounds);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                FlagItApplication.getInstance().match = null;
                mMatch = null;
                startActivity(intent);
                break;
            case R.id.maps_btn_go:
                mMatch.matchState = FlagMatch.MatchState.IN_PROGRESS;
                nextRound();
                v.setVisibility(View.GONE);
                break;
            default:
                new ParticleSystem(this, 100, R.drawable.star_white, 800)
                        .setSpeedRange(0.1f, 0.25f)
                        .oneShot(v, 100);

        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        this.mMotionEvent = ev;
        return super.dispatchTouchEvent(ev);
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(EventStartMatch event) {
        Log.d(TAG, "ENTER onEventBackgroundThread");


        FlagMatch match = new FlagMatch(getResources(), event.gameType);
        EventBus.getDefault().post(new EventStartMatchResponse(match));
        Log.d(TAG, "RETURN onEventBackgroundThread");
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(EventStartMatchResponse event) {
        FlagItApplication.getInstance().match = event.match;
        this.mMatch = event.match;

        //animate camera to show new area
        final CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(mMatch.bounds, mPadding);
        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                mMap.animateCamera(cu);
            }
        });
    }

    private void nextRound() {
        Log.d(TAG, "nextRound");
        if (mMatch.matchState == FlagMatch.MatchState.COMPLETED) {
            mBtnResults.setVisibility(View.VISIBLE);
        } else {
            mMatch.clicked = false;
            mGameTimer = new GameTimer(mMatch.count_down_ms, true, mMatch.tick_interval_ms);
            mGameTimer.startIfNotStarted();
            updateCenterText();
        }

    }

    private class EventStartMatch {
        final GameType gameType;

        public EventStartMatch(GameType gameType) {
            this.gameType = gameType;
        }
    }

    private class EventStartMatchResponse {
        final FlagMatch match;

        public EventStartMatchResponse(FlagMatch match) {
            this.match = match;
        }
    }

    private class GameTimer extends CountDownTimer {
        public final boolean preRound;
        private final String TAG = "GameTimer";
        public long timeEllapsed = 0;
        public long millisUntilFinished;
        boolean started = false;

        public GameTimer(long time, boolean preRound, int tickIntervalMs) {
            super(time, tickIntervalMs);
            millisUntilFinished = time;
            this.preRound = preRound;
        }

        synchronized public void startIfNotStarted() {
            if (!started) {
                timeIndicator.setVisibility(View.VISIBLE);

                started = true;
                super.start();
            }

        }

        public void onTick(long millisUntilFinished) {

            timeEllapsed += mMatch.tick_interval_ms;
            this.millisUntilFinished = millisUntilFinished;
            float percentageRemaining = (float) millisUntilFinished / (Math.abs(millisUntilFinished + timeEllapsed));
            int width = (int) (indicatorWidth * percentageRemaining);
            timeIndicator.getLayoutParams().width = width;
            timeIndicator.requestLayout();

        }

        @Override
        public void onFinish() {
            Log.d(TAG, "onFinish()");
            timeIndicator.setVisibility(View.GONE);
            if (preRound) {
                Log.d(TAG, "StartRound()");
                mMatch.roundInProgress = true;
                mMap.clear();
                map_hit.setText("");
                map_hit.setVisibility(View.INVISIBLE);
                mGameTimer = new GameTimer(mMatch.round_time_ms, false, mMatch.tick_interval_ms);
                mGameTimer.startIfNotStarted();
                updateCenterText();
            } else {
                //No click in time, report timeout result
                RoundResult result = mMatch.roundTimeout();
                map_hit.setVisibility(View.VISIBLE);
                map_hit.setTextColor(FlagItUtils.getAccuracyColor(MapsActivity.this, RoundResult.Accuracy.TIME_OUT));
                map_hit.setText("Timeout");
                RelativeLayout.LayoutParams layoutParams =
                        (RelativeLayout.LayoutParams) map_hit_container.getLayoutParams();
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                map_hit_container.setLayoutParams(layoutParams);

                YoYo.with(Techniques.Shake)
                        .duration(700)
                        .playOn(map_hit);

                FlagItUtils.drawPoly(result, mMap, MapsActivity.this);
                nextRound();
            }
        }
    }

}