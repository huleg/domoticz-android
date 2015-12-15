/*
 * Copyright (C) 2015 Domoticz
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package nl.hnogames.domoticz.Fragments;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;

import nl.hnogames.domoticz.Adapters.TemperatureAdapter;
import nl.hnogames.domoticz.Containers.GraphPointInfo;
import nl.hnogames.domoticz.Containers.TemperatureInfo;
import nl.hnogames.domoticz.Domoticz.Domoticz;
import nl.hnogames.domoticz.Interfaces.DomoticzFragmentListener;
import nl.hnogames.domoticz.Interfaces.GraphDataReceiver;
import nl.hnogames.domoticz.Interfaces.TemperatureClickListener;
import nl.hnogames.domoticz.Interfaces.TemperatureReceiver;
import nl.hnogames.domoticz.Interfaces.setCommandReceiver;
import nl.hnogames.domoticz.R;
import nl.hnogames.domoticz.UI.GraphDialog;
import nl.hnogames.domoticz.UI.TemperatureInfoDialog;
import nl.hnogames.domoticz.app.DomoticzFragment;

public class Temperature extends DomoticzFragment implements DomoticzFragmentListener, TemperatureClickListener {

    private static final String TAG = Temperature.class.getSimpleName();
    private ProgressDialog progressDialog;
    private Domoticz mDomoticz;
    private Context mActivity;

    private ArrayList<TemperatureInfo> mTemperatureInfos;
    private ListView listView;
    private TemperatureAdapter adapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private CoordinatorLayout coordinatorLayout;

    @Override
    public void refreshFragment() {
        if (mSwipeRefreshLayout != null)
            mSwipeRefreshLayout.setRefreshing(true);

        processTemperature();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        getActionBar().setTitle(R.string.title_temperature);
    }

    @Override
    public void Filter(String text) {
        try {
            if (adapter != null)
                adapter.getFilter().filter(text);
            super.Filter(text);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onConnectionOk() {
        showProgressDialog();

        mDomoticz = new Domoticz(mActivity);
        processTemperature();
    }

    private void processTemperature() {
        final TemperatureClickListener listener = this;

        mDomoticz.getTemperatures(new TemperatureReceiver() {

            @Override
            public void onReceiveTemperatures(ArrayList<TemperatureInfo> mTemperatureInfos) {
                successHandling(mTemperatureInfos.toString(), false);
                Temperature.this.mTemperatureInfos = mTemperatureInfos;

                if (getView() != null) {
                    mSwipeRefreshLayout = (SwipeRefreshLayout) getView().findViewById(R.id.swipe_refresh_layout);
                    coordinatorLayout = (CoordinatorLayout) getView().findViewById(R.id
                            .coordinatorLayout);

                    adapter = new TemperatureAdapter(mActivity, mTemperatureInfos, listener);
                    listView = (ListView) getView().findViewById(R.id.listView);
                    listView.setAdapter(adapter);
                    listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                        @Override
                        public boolean onItemLongClick(AdapterView<?> adapterView, View view,
                                                       int index, long id) {
                            showInfoDialog(adapter.filteredData.get(index));
                            return true;
                        }
                    });
                    mSwipeRefreshLayout.setRefreshing(false);

                    mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            processTemperature();
                        }
                    });
                    hideProgressDialog();
                }
            }

            @Override
            public void onError(Exception error) {
                errorHandling(error);
            }
        });
    }

    private void showInfoDialog(final TemperatureInfo mTemperatureInfo) {
        TemperatureInfoDialog infoDialog = new TemperatureInfoDialog(
                getActivity(),
                mTemperatureInfo,
                R.layout.dialog_utilities_info);
        infoDialog.setIdx(String.valueOf(mTemperatureInfo.getIdx()));
        infoDialog.setLastUpdate(mTemperatureInfo.getLastUpdate());
        infoDialog.setIsFavorite(mTemperatureInfo.getFavoriteBoolean());
        infoDialog.show();
        infoDialog.onDismissListener(new TemperatureInfoDialog.DismissListener() {
            @Override
            public void onDismiss(boolean isChanged, boolean isFavorite) {
                if (isChanged)
                    changeFavorite(mTemperatureInfo, isFavorite);
            }
        });
    }

    private void changeFavorite(final TemperatureInfo mTemperatureInfo, final boolean isFavorite) {
        addDebugText("changeFavorite");
        addDebugText("Set idx " + mTemperatureInfo.getIdx() + " favorite to " + isFavorite);

        if (isFavorite)
            Snackbar.make(coordinatorLayout, mTemperatureInfo.getName() + " " + getActivity().getString(R.string.favorite_added), Snackbar.LENGTH_SHORT).show();
        else
            Snackbar.make(coordinatorLayout, mTemperatureInfo.getName() + " " + getActivity().getString(R.string.favorite_removed), Snackbar.LENGTH_SHORT).show();

        int jsonAction;
        int jsonUrl = Domoticz.Json.Url.Set.FAVORITE;

        if (isFavorite) jsonAction = Domoticz.Device.Favorite.ON;
        else jsonAction = Domoticz.Device.Favorite.OFF;

        mDomoticz.setAction(mTemperatureInfo.getIdx(), jsonUrl, jsonAction, 0, new setCommandReceiver() {
            @Override
            public void onReceiveResult(String result) {
                successHandling(result, false);
                mTemperatureInfo.setFavoriteBoolean(isFavorite);
            }

            @Override
            public void onError(Exception error) {
                errorHandling(error);
            }
        });
    }


    /**
     * Initializes the progress dialog
     */
    private void initProgressDialog() {
        progressDialog = new ProgressDialog(this.getActivity());
        progressDialog.setMessage(getString(R.string.msg_please_wait));
        progressDialog.setCancelable(false);
    }

    /**
     * Shows the progress dialog if isn't already showing
     */
    private void showProgressDialog() {
        if (progressDialog == null) initProgressDialog();
        if (!progressDialog.isShowing())
            progressDialog.show();
    }

    /**
     * Hides the progress dialog if it is showing
     */
    private void hideProgressDialog() {
        if (progressDialog.isShowing())
            progressDialog.dismiss();
    }

    @Override
    public void errorHandling(Exception error) {
        super.errorHandling(error);
        hideProgressDialog();
    }

    @Override
    public void onLogClick(final TemperatureInfo temp, final String range) {
        showProgressDialog();
        mDomoticz.getGraphData(temp.getIdx(), range, "temp", new GraphDataReceiver() {
            @Override
            public void onReceive(ArrayList<GraphPointInfo> mGraphList) {
                hideProgressDialog();
                GraphDialog infoDialog = new GraphDialog(
                        getActivity(),
                        mGraphList,
                        R.layout.dialog_graph);
                infoDialog.setRange(range);
                infoDialog.setSteps(3);
                infoDialog.setTitle(getActivity().getString(R.string.title_temperature));
                infoDialog.show();
            }

            @Override
            public void onError(Exception error) {
                Snackbar.make(coordinatorLayout, getActivity().getString(R.string.error_log)+": " + temp.getName(), Snackbar.LENGTH_SHORT).show();
                hideProgressDialog();
            }
        });
    }
}