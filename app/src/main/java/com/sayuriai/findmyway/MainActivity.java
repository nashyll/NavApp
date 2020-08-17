package com.sayuriai.findmyway;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import java.security.Permissions;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationEngineListener, PermissionsListener, MapboxMap.OnMapClickListener {


    private MapView mapView;
    private MapboxMap map;
    private Button startButton;
    private PermissionsManager permissionsManager;
    private LocationEngine locationEngine;
    private LocationLayerPlugin locationLayerPlugin;
    private Location originLocation;
    private Point originPosition;
    private Point destinationPosition;
    private Marker destinationMarker;
    private NavigationMapRoute navigationMapRoute;
    FirebaseAuth mAuth;
    private Toolbar mToolbar;
    private static final String TAG = "MainActivity";
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
       if(user == null){
           //Go to login page
           Intent intent = new Intent(MainActivity.this, LoginActivity.class);
           startActivity(intent);
           finish();
       }
       else{
           mToolbar = (Toolbar) findViewById ( R.id.toolbar );
           setSupportActionBar ( mToolbar );
//           getSupportActionBar().setDisplayHomeAsUpEnabled ( true );
//           getSupportActionBar ().setDisplayShowHomeEnabled(true);
           Mapbox.getInstance( this, getString(R.string.access_token));
           mapView = findViewById(R.id.mapView);
           startButton = findViewById(R.id.startButton);
           mapView.onCreate(savedInstanceState);
           mapView.getMapAsync(this);
           startButton.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View v) {

                   NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                           .origin(originPosition)
                           .destination(destinationPosition)
                           .shouldSimulateRoute(true)
                           .build();
                   NavigationLauncher.startNavigation(MainActivity.this, options);


               }
           });

       }


    }
    /**
     *  Inflate the menu; this adds items to the action bar if it is present.
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return  true;
    }

    /**
     * Setup the main menu bar
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            mAuth.signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onMapReady(MapboxMap mapboxMap) {

        map = mapboxMap;
        map.addOnMapClickListener(this);
        enableLocation();


    }

    private void enableLocation(){

        if (PermissionsManager.areLocationPermissionsGranted(this)){

            initializeLocationEngine();
            initializeLocationLayer();

        }else {

            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);

        }

    }

    @SuppressWarnings("MissingPermission")
    private void initializeLocationEngine(){

        locationEngine = new LocationEngineProvider(this).obtainBestLocationEngineAvailable();
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.activate();

        Location lastLocation = locationEngine.getLastLocation();
        if(lastLocation != null){

            originLocation = lastLocation;
            setCameraPosition(lastLocation);


        }else{

            locationEngine.addLocationEngineListener(this);

        }

    }

    @SuppressWarnings("MissingPermission")
    private void initializeLocationLayer(){

        locationLayerPlugin = new LocationLayerPlugin(mapView, map, locationEngine);
        locationLayerPlugin.setLocationLayerEnabled(true);
        locationLayerPlugin.setCameraMode(CameraMode.TRACKING);
        locationLayerPlugin.setRenderMode(RenderMode.NORMAL);

    }



    private void setCameraPosition(Location location){

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()),13.0));


    }


    @Override
    public void onMapClick(@NonNull LatLng point) {

        if(destinationMarker != null){
            map.removeMarker(destinationMarker);
        }
        destinationMarker = map.addMarker(new MarkerOptions().position(point));
        destinationPosition = Point.fromLngLat(point.getLongitude(), point.getLatitude());
        originPosition = Point.fromLngLat(originLocation.getLongitude(), originLocation.getLatitude());
        getRoute(originPosition, destinationPosition);
        startButton.setEnabled(true);
        startButton.setBackgroundResource(R.color.mapboxBlue);

    }
    private void getRoute(Point origin, Point destination){

        NavigationRoute.builder().accessToken(Mapbox.getAccessToken()).origin(origin).destination(destination).build().getRoute(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {

                if(response.body() == null){

                    Log.e(TAG, "No routes found, check right user and access token");
                    return;

                }else if(response.body().routes().size() == 0){

                    Log.e(TAG, "No routes found");
                    return;

                }

                DirectionsRoute currentRoute = response.body().routes().get(0);

                if(navigationMapRoute != null){

                    navigationMapRoute.removeRoute();

                }else {

                    navigationMapRoute = new NavigationMapRoute(null, mapView, map);

                }

                navigationMapRoute.addRoute(currentRoute);

            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {

                Log.e(TAG, "Error: "+ t.getMessage());

            }
        });

    }




    @Override
    @SuppressWarnings("MissingPermission")
    public void onConnected() {

        locationEngine.requestLocationUpdates();

    }

    @Override
    public void onLocationChanged(Location location) {

        if(location != null){

            originLocation = location;
            setCameraPosition(location);

        }

    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {

        //present toast


    }

    @Override
    public void onPermissionResult(boolean granted) {

        if(granted){

            enableLocation();

        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    @SuppressWarnings("MissingPermission")
    protected void onStart(){
        super.onStart();
        if (locationEngine != null){

            locationEngine.requestLocationUpdates();

        }
        if (locationLayerPlugin != null){

            locationLayerPlugin.onStart();

        }


        mapView.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(locationEngine != null){

            locationEngine.removeLocationUpdates();

        }
        if(locationLayerPlugin != null){
            locationLayerPlugin.onStop();

        }
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationEngine != null){
            locationEngine.deactivate();
        }
    }


}