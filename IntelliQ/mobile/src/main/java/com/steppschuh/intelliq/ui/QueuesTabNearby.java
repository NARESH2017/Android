package com.steppschuh.intelliq.ui;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionInflater;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.exception.NetworkException;
import com.octo.android.robospice.exception.NoNetworkException;
import com.octo.android.robospice.exception.RequestCancelledException;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;
import com.steppschuh.intelliq.IntelliQ;
import com.steppschuh.intelliq.R;
import com.steppschuh.intelliq.api.JsonSpiceService;
import com.steppschuh.intelliq.api.entry.BusinessEntry;
import com.steppschuh.intelliq.api.entry.QueueEntry;
import com.steppschuh.intelliq.api.request.NearbyQueuesRequest;
import com.steppschuh.intelliq.api.response.BusinessListApiResponse;
import com.steppschuh.intelliq.api.user.User;
import com.steppschuh.intelliq.ui.widget.BusinessItemQueueView;
import com.steppschuh.intelliq.ui.widget.StatusHelper;
import com.steppschuh.intelliq.ui.widget.StatusView;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;

public class QueuesTabNearby extends Fragment implements SwipeRefreshLayout.OnRefreshListener, User.LocationChangedListener {

    IntelliQ app;

    protected SpiceManager spiceManager = new SpiceManager(JsonSpiceService.class);
    private String lastRequestCacheKey;

    RecyclerView recyclerView;
    BusinessListAdapter businessListAdapter;
    SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_queues_tab_nearby, container,false);

        app = (IntelliQ) getActivity().getApplication();

        return setupUi(v);
    }

    private View setupUi(View v) {
        ArrayList<BusinessEntry> businessEntries = new ArrayList<>();
        businessListAdapter = new BusinessListAdapter(businessEntries);
        businessListAdapter.setOnItemClickListener(new BusinessItemQueueView.OnItemClickListener() {

            /**
             * Show the queue details fragment if an item was clicked
             */
            @Override
            public void onItemClick(QueueEntry queueEntry, BusinessItemQueueView businessItemQueueView) {
                // get the queues parent business entry
                BusinessEntry parentBusinessEntry = null;
                for (BusinessEntry businessEntry : businessListAdapter.getBusinessEntries()) {
                    if (businessEntry.getKey().getId() == queueEntry.getBusinessKeyId()) {
                        parentBusinessEntry = businessEntry;
                    }
                }

                // create a new details fragment for that queue
                QueuesDetailsFragment queuesDetailsFragment = new QueuesDetailsFragment();
                queuesDetailsFragment.setBusinessEntry(parentBusinessEntry);
                queuesDetailsFragment.setQueueEntry(queueEntry);

                // setup the fragment transaction
                FragmentTransaction trans;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Set shared and scene transitions on this fragment
                    setSharedElementReturnTransition(TransitionInflater.from(getActivity()).inflateTransition(R.transition.trans_move));
                    setExitTransition(TransitionInflater.from(getActivity()).inflateTransition(android.R.transition.fade));

                    // Set shared and scene transitions on new fragment
                    ImageView image = businessItemQueueView.getQueueImage();
                    queuesDetailsFragment.setSharedElementEnterTransition(TransitionInflater.from(getActivity()).inflateTransition(R.transition.trans_move));
                    queuesDetailsFragment.setEnterTransition(TransitionInflater.from(getActivity()).inflateTransition(android.R.transition.fade));
                    queuesDetailsFragment.setImageTransitionName(image.getTransitionName());

                    trans = getActivity().getSupportFragmentManager().beginTransaction();
                    //trans.replace(R.id.coordinatorLayout, queuesDetailsFragmentFragment);
                    trans.add(R.id.contentRoot, queuesDetailsFragment, QueuesDetailsFragment.class.getSimpleName());
                    trans.addToBackStack(null);
                    trans.addSharedElement(image, image.getTransitionName());
                } else {
                    trans = getActivity().getSupportFragmentManager().beginTransaction();
                    //trans.replace(R.id.coordinatorLayout, queuesDetailsFragment);
                    trans.add(R.id.contentRoot, queuesDetailsFragment, QueuesDetailsFragment.class.getSimpleName());
                    trans.addToBackStack(null);
                }

                // perform the transaction
                trans.commit();
            }

        });

        recyclerView = (RecyclerView) v.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(businessListAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        //recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        // set number of columns
        DisplayMetrics displayMetrics = getActivity().getResources().getDisplayMetrics();
        float screenWidth = displayMetrics.widthPixels / displayMetrics.density;
        float itemWidth = 400;
        int numberOfColumns = (int) Math.floor(screenWidth / itemWidth);
        numberOfColumns = Math.max(1, numberOfColumns);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), numberOfColumns);

        recyclerView.setLayoutManager(gridLayoutManager);

        swipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.accent);

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        spiceManager.start(getContext());
        app.getUser().registerLocationChangedListener(this);
        onRefresh();
    }

    @Override
    public void onStop() {
        if (spiceManager.isStarted()) {
            spiceManager.shouldStop();
        }
        app.getUser().unregisterLocationChangedListener(this);
        super.onStop();
    }

    @Override
    public void onRefresh() {
        if (businessListAdapter.getBusinessEntries() == null || businessListAdapter.getBusinessEntries().size() == 0) {
            // show loading status view
            StatusHelper.showLoadingQueuesStatus(getActivity(), businessListAdapter, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onRefresh();
                }
            });
        }

        // this will trigger a location update and onLocationChanged will be called
        app.getUser().updateLocation(getActivity());
    }

    @Override
    public void onLocationChanged(float latitude, float longitude) {
        Log.d(IntelliQ.TAG, "onLocationChanged: " + String.valueOf(latitude) + " , " + String.valueOf(latitude));
        requestQueues();
    }

    @Override
    public void onLocationChanged(String postalCode) {
        Log.d(IntelliQ.TAG, "onLocationChanged: " + postalCode);
        requestQueues();
    }

    private void requestQueues() {
        swipeRefreshLayout.setRefreshing(true);
        NearbyQueuesRequest request = null;

        // check if some location info is available
        if (app.getUser().hasValidLocation()) {
            // latitude & longitude set, awesome
            request = new NearbyQueuesRequest(app.getUser().getLatitude(), app.getUser().getLongitude(), QueueEntry.DISTANCE_DEFAULT);
        } else if (app.getUser().hasValidPostalCode()) {
            // at least some postal code, let's hope we find something
            request = new NearbyQueuesRequest(app.getUser().getPostalCode());
        } else {
            // no location info at all, show error messages
            if (app.getUser().hasGrantedLocationPermission(getActivity())) {
                // we have the permission but no data
                StatusHelper.showUnknownLocationError(getActivity(), businessListAdapter, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRefresh();
                    }
                });
                app.getUser().requestPostalCode(getActivity());
            } else {
                // permission denied
                StatusHelper.showMissingLocationPermissionError(getActivity(), businessListAdapter, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        app.getUser().openPermissionSettings(getActivity());
                    }
                });
            }
        }

        // actually request some data
        if (request != null) {
            lastRequestCacheKey = request.createCacheKey();
            spiceManager.execute(request, lastRequestCacheKey, DurationInMillis.ONE_MINUTE, new ApiResponseListener());
            Log.d(IntelliQ.TAG, "Requesting nearby businessEntries with cache key: " + lastRequestCacheKey);
        }
    }

    private class ApiResponseListener implements RequestListener<BusinessListApiResponse> {

        @Override
        public void onRequestFailure(SpiceException e) {
            // something went wrong, try to find out what
            if (e.getCause() instanceof HttpClientErrorException) {
                // some network error
                HttpClientErrorException exception = (HttpClientErrorException) e.getCause();
                Log.e(IntelliQ.TAG, "HttpClientErrorException: " + exception.getMessage() + " (" + exception.getStatusCode() + ")");

                StatusView status = StatusHelper.getNetworkError(getActivity());
                status.getStatusSubHeading().setText(status.getStatusSubHeading().getText() + "\n\nException: " + exception.getMessage());
                StatusHelper.showStatus(status, businessListAdapter, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRefresh();
                    }
                });
            } else if (e instanceof RequestCancelledException) {
                // don't show an error message, just retry
                Log.e(IntelliQ.TAG, "RequestCancelledException: " + e.getMessage());
                onRefresh();
            } else if (e instanceof NetworkException || e instanceof NoNetworkException) {
                Log.e(IntelliQ.TAG, "NetworkException: " + e.getMessage());
                StatusHelper.showNetworkError(getActivity(), businessListAdapter, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRefresh();
                    }
                });
            } else {
                // damn, unknown error
                Log.e(IntelliQ.TAG, "Unknown onRequestFailure: " + e.getMessage());
                StatusView status = StatusHelper.getUnknownError(getActivity());
                status.getStatusSubHeading().setText(status.getStatusSubHeading().getText() + "\n\nException: " + e.getMessage());
                StatusHelper.showStatus(status, businessListAdapter, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRefresh();
                    }
                });
            }

            swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onRequestSuccess(BusinessListApiResponse response) {
            if (response.getStatusCode() == HttpStatus.OK.value())  {
                // API call was successful
                if (response.getContent().size() > 0) {
                    // we got some data
                    businessListAdapter.setBusinessEntries(response.getContent());
                    businessListAdapter.notifyDataSetChanged();
                } else {
                    // request was alright, but no businessEntries found
                    StatusHelper.showNoDataError(getActivity(), businessListAdapter, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onRefresh();
                        }
                    });
                }
            } else {
                // API returned an error code
                StatusView status = StatusHelper.getApiError(getActivity());
                status.getStatusSubHeading().setText(status.getStatusSubHeading().getText() + "\n\nException: " + response.getStatusMessage());
                StatusHelper.showStatus(status, businessListAdapter, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRefresh();
                    }
                });
            }

            swipeRefreshLayout.setRefreshing(false);

            if (QueuesTabNearby.this.isAdded()) {
                //update your UI
            }
        }
    }

}