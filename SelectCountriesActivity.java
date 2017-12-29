package com.mysafetravel.falck.activity;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mysafetravel.falck.R;
import com.mysafetravel.falck.api.Api;
import com.mysafetravel.falck.api.ApiClientCache;
import com.mysafetravel.falck.api.model.Country;
import com.mysafetravel.falck.widget.Section;
import com.mysafetravel.falck.widget.SectionParameters;
import com.mysafetravel.falck.widget.SectionedRecyclerViewAdapter;
import com.mysafetravel.falck.widget.StatelessSection;

import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RetrofitError;

/**
 * Created by jhh on 07/05/15.
 **/
public class SelectCountriesActivity extends ToolbarActivity {
    public final static int SELECT_COUNTRIES_REQUEST_CODE = 7313;
    public final static String EXTRA_SELECTED_COUNTRIES = "selected_countries";
    public final static String EXTRA_IS_SINGLE_SELECTION = "single_selection";
    public final static String EXTRA_RESULT_SELECTED_COUNTRIES = "selected_countries";
    private static final String FIRST_TAG = "Selected";
    private static final String SECOND_TAG = "Available";

    private RelativeLayout loadingLayout;
    private List<Country> countries = null;
    private ArrayList<Country> selectedCountries = new ArrayList<>();
    private SearchView searchView;
    private RecyclerView rvCountriesList;


    private SectionedRecyclerViewAdapter sectionAdapter;

    private boolean isSingleSelection;

    private SearchView.OnQueryTextListener searchListener = new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            searchView.clearFocus();
            return true;
        }

        @Override
        public boolean onQueryTextChange(String query) {
            for (Section section : sectionAdapter.getSectionsMap().values()) {
                if (section instanceof FilterableSection) {
                    ((FilterableSection) section).filter(query);
                }
            }
            sectionAdapter.notifyDataSetChanged();
            return true;
        }
    };

    private ContactsSection sectionAC;
    private ContactsSection sectionStart;

    public SelectCountriesActivity() {
        setLayoutResourceId(R.layout.activity_select_countries);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context mContext = SelectCountriesActivity.this;
        sectionAdapter = new SectionedRecyclerViewAdapter();
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();

        if (extras != null) {
            if (extras.containsKey(EXTRA_IS_SINGLE_SELECTION)) {
                isSingleSelection = extras.getBoolean(EXTRA_IS_SINGLE_SELECTION);
            }

            if (extras.containsKey(EXTRA_SELECTED_COUNTRIES)) {
                final Object[] selectedObjects = (Object[]) extras.getSerializable(EXTRA_SELECTED_COUNTRIES);

                if (selectedObjects != null) {
                    for (Object country : selectedObjects) {
                        this.selectedCountries.add((Country) country);
                    }
                } else {
                    Toast.makeText(mContext, "No country selected", Toast.LENGTH_SHORT).show();
                }
            }

            //if (selectedCountries.size() > 0) {
            sectionStart = new ContactsSection("Selected Countries", selectedCountries);
            sectionAdapter.addSection(FIRST_TAG, sectionStart);
            // }
        }

        setTitle(R.string.title_activity_select_countries);
        final Toolbar toolbar = getToolbar();
        toolbar.inflateMenu(R.menu.menu_select_countries);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.action_save) {
                    save();
                }

                return true;
            }
        });

        final Menu menu = toolbar.getMenu();
        final MenuItem searchItem = menu.findItem(R.id.action_search);

        MenuItem saveItem = menu.findItem(R.id.action_save);

        if (isSingleSelection) {
            saveItem.setVisible(false);
        }

        loadingLayout = (RelativeLayout) findViewById(R.id.select_countries_loading_layout);
        rvCountriesList = (RecyclerView) findViewById(R.id.select_countries_list);
        rvCountriesList.setLayoutManager(new LinearLayoutManager(mContext));
        rvCountriesList.setAdapter(sectionAdapter);

        final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();

            if (searchView != null) {
                if (searchManager != null) {
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
                }
                searchView.setOnQueryTextListener(searchListener);
                ImageView closeButton = (ImageView) searchView.findViewById(R.id.search_close_btn);
                closeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        searchView.onActionViewCollapsed();
                    }
                });
            }
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 500);
    }

    private class ContactsSection extends StatelessSection implements FilterableSection {
        String title;
        List<Country> list;
        List<Country> filteredList;

        ContactsSection(String title, List<Country> list) {
            super(new SectionParameters.Builder(R.layout.row_country_selection).headerResourceId(R.layout.separator_layout).build());
            this.title = title;
            this.list = list;
            filteredList = new ArrayList<>(list);
        }

        void setTitle(String title) {
            this.title = title;
        }

        String getTitle() {
            return title;
        }

        @Override
        public int getContentItemsTotal() {
            return filteredList.size();
        }

        @Override
        public RecyclerView.ViewHolder getItemViewHolder(View view) {
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindItemViewHolder(RecyclerView.ViewHolder holder, final int position) {
            final ItemViewHolder itemHolder = (ItemViewHolder) holder;
            final Country country = filteredList.get(position);
            determineStatus(itemHolder.tvItem, itemHolder.imgItem, country);
            String name = country.getName();
            itemHolder.tvItem.setText(name);
            itemHolder.imgItem.setImageResource(R.drawable.checkmark);

            if (selectedCountries.size() == 0) {
                sectionAdapter.removeSection(FIRST_TAG);
            }

            itemHolder.rootView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedCountries.contains(country)) {
                        selectedCountries.remove(country);
                    } else {
                        selectedCountries.add(country);
                    }

                    if (selectedCountries.size() > 0) {
                        sectionAdapter.removeAllSections();
                        sectionAdapter.addSection(FIRST_TAG, sectionStart);
                        sectionAdapter.addSection(SECOND_TAG, sectionAC);
                    } else {
                        sectionAdapter.removeSection(FIRST_TAG);
                    }

                    sectionStart.invalidateList(selectedCountries);

                    determineStatus(itemHolder.tvItem, itemHolder.imgItem, country);

                    if (isSingleSelection) {
                        save();
                    }
                }
            });
        }

        void invalidateList(List<Country> listNew) {
            if (filteredList != null) {
                filteredList.clear();
                filteredList.addAll(listNew);
                sectionAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public RecyclerView.ViewHolder getHeaderViewHolder(View view) {
            return new HeaderViewHolder(view);
        }

        @Override
        public void onBindHeaderViewHolder(RecyclerView.ViewHolder holder) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            if (title.equalsIgnoreCase("")) {
                headerHolder.rlHead.setVisibility(View.GONE);
            } else {
                headerHolder.rlHead.setVisibility(View.VISIBLE);
                headerHolder.tvTitle.setText(title);
            }
        }

        @Override
        public void filter(String query) {
            if (TextUtils.isEmpty(query)) {
                filteredList = new ArrayList<>(list);
                this.setVisible(true);
            } else {
                filteredList.clear();
                for (Country c : list) {
                    if (c.getName().toLowerCase().contains(query.toLowerCase())) {
                        filteredList.add(c);
                    }
                }
                this.setVisible(!filteredList.isEmpty());
            }
        }
    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private RelativeLayout rlHead;

        HeaderViewHolder(View view) {
            super(view);
            tvTitle = (TextView) view.findViewById(R.id.tvTitle);
            rlHead = (RelativeLayout) view.findViewById(R.id.rl_head);
        }
    }

    private class ItemViewHolder extends RecyclerView.ViewHolder {
        private final View rootView;
        private final ImageView imgItem;
        private final TextView tvItem;

        ItemViewHolder(View view) {
            super(view);
            rootView = view;
            imgItem = (ImageView) view.findViewById(R.id.checkmark);
            tvItem = (TextView) view.findViewById(R.id.country_name);
        }
    }


    private void refresh() {
        loadingLayout.setVisibility(View.VISIBLE);
        ApiClientCache.getCountries(true,
                new Callback<Api.ResponseCountries>() {
                    @Override
                    public void success(Api.ResponseCountries responseCountries, retrofit.client.Response response) {
                        countries = responseCountries.getCountries();
                        sectionAC = new ContactsSection("Available Countries", countries);
                        sectionAdapter.addSection(SECOND_TAG, sectionAC);
                        rvCountriesList.setAdapter(sectionAdapter);
                        loadingLayout.setVisibility(View.GONE);
                    }

                    @Override
                    public void failure(RetrofitError error) {

                    }
                });
    }

    private void save() {
        finishWithCountries(selectedCountries.toArray(new Country[selectedCountries.size()]));
    }

    private void finishWithCountries(final Country[] countries) {
        final Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_SELECTED_COUNTRIES, countries);
        setResult(RESULT_OK, data);
        finish();
    }

    private void determineStatus(TextView title, ImageView checkMark, Country country) {
        final boolean valueIsSelected = selectedCountries != null && selectedCountries.contains(country);

        if (title != null) {
            title.setText(country.getName());
            title.setTypeface(null, valueIsSelected ? Typeface.BOLD : Typeface.NORMAL);
            title.setTextColor(getResources().getColor(valueIsSelected ? R.color.blue : R.color.text));
        }

        if (checkMark != null) {
            checkMark.setVisibility(valueIsSelected ? View.VISIBLE : View.GONE);
        }
    }

    interface FilterableSection {
        void filter(String query);
    }
}