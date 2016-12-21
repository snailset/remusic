package com.wm.remusic.fragment;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bilibili.magicasakura.utils.ThemeUtils;
import com.wm.remusic.R;
import com.wm.remusic.adapter.MainFragmentAdapter;
import com.wm.remusic.adapter.MainFragmentItem;
import com.wm.remusic.info.Playlist;
import com.wm.remusic.provider.DownFileStore;
import com.wm.remusic.provider.PlaylistInfo;
import com.wm.remusic.recent.TopTracksLoader;
import com.wm.remusic.uitl.IConstants;
import com.wm.remusic.uitl.MusicUtils;
import com.wm.remusic.widget.DividerItemDecoration;
import com.wm.remusic.widget.SideBar;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wm
 *         Created by wm on 2016/3/8.
 *         本地界面主界面
 */
public class MainFragment extends BaseFragment {

    private MainFragmentAdapter mAdapter;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private List<MainFragmentItem> mList = new ArrayList<>();
    private PlaylistInfo playlistInfo; //playlist 管理类
    private SwipeRefreshLayout swipeRefresh; //下拉刷新layout
    private Context mContext;
    private SideBar sideBar;
    private TextView dialogText;


    /**
     * 6.0以后的权限请求
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCount();
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getContext();
        playlistInfo = PlaylistInfo.getInstance(mContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swiperefresh);
        recyclerView = (RecyclerView) view.findViewById(R.id.recyclerview);
        layoutManager = new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(layoutManager);
        //swipeRefresh.setColorSchemeResources(R.color.theme_color_PrimaryAccent);
        swipeRefresh.setColorSchemeColors(ThemeUtils.getColorById(mContext,R.color.theme_color_primary));
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reloadAdapter();

            }
        });
        sideBar = (SideBar) view.findViewById(R.id.sidebar);
        dialogText = (TextView) view.findViewById(R.id.dialog_text);
        //先给adapter设置空数据，异步加载好后更新数据，防止Recyclerview no attach
        mAdapter = new MainFragmentAdapter(mContext, null, null);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.addItemDecoration(new DividerItemDecoration(mContext, DividerItemDecoration.VERTICAL_LIST));
        reloadAdapter();

        sideBar.setOnTouchingLetterChangedListener(new SideBar.OnTouchingLetterChangedListener() {
            @Override
            public void onTouchingLetterChanged(String s) {
                dialogText.setText(s);
                sideBar.setView(dialogText);
            }
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if(newState == RecyclerView.SCROLL_STATE_DRAGGING){
                    sideBar.setVisibility(View.VISIBLE);
                }else if(newState == RecyclerView.SCROLL_STATE_IDLE){
                    sideBar.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sideBar.setVisibility(View.INVISIBLE);
                        }
                    },2000);
                }
            }
        });


        getActivity().getWindow().setBackgroundDrawableResource(R.color.background_material_light_1);
        return view;
    }


    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            //相当于Fragment的onResume
            reloadAdapter();
        }
    }



    //为info设置数据，并放入mlistInfo
    private void setInfo(String title, int count, int id, int i) {
        MainFragmentItem information = new MainFragmentItem();
        information.title = title;
        information.count = count;
        information.avatar = id;
        if (mList.size() < 4) {
            mList.add(new MainFragmentItem());
        }
        mList.set(i, information); //将新的info对象加入到信息列表中
    }

    //设置音乐overflow条目
    private void setMusicInfo() {

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        } else {
            loadCount();
        }
    }

    private void loadCount() {
        int localMusicCount = MusicUtils.queryMusic(mContext, IConstants.START_FROM_LOCAL).size();
        int recentMusicCount = TopTracksLoader.getCursor(mContext,TopTracksLoader.QueryType.RecentSongs).getCount();
        int downLoadCount = DownFileStore.getInstance(mContext).getDownLoadedListAll().size();
        int artistsCount = MusicUtils.queryArtist(mContext).size();
        setInfo(mContext.getResources().getString(R.string.local_music), localMusicCount, R.drawable.music_icn_local, 0);
        setInfo(mContext.getResources().getString(R.string.recent_play), recentMusicCount, R.drawable.music_icn_recent, 1);
        setInfo(mContext.getResources().getString(R.string.local_manage), downLoadCount, R.drawable.music_icn_dld, 2);
        setInfo(mContext.getResources().getString(R.string.my_artist), artistsCount, R.drawable.music_icn_artist, 3);
    }

    //刷新列表
    public void reloadAdapter() {
        if (mAdapter == null) {  //todo  开始mAdapter没初始化的时候就调用了 蛋疼  这个方法为什么无缘无故被调用好几次
            return;
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... unused) {
                ArrayList results = new ArrayList();
                setMusicInfo();
                ArrayList<Playlist> playlists = playlistInfo.getPlaylist();
                ArrayList<Playlist> netPlaylists = playlistInfo.getNetPlaylist();
                results.addAll(mList);
                results.add(mContext.getResources().getString(R.string.created_playlists));
                results.addAll(playlists);
                if (netPlaylists != null && netPlaylists.size() > 0) {
                    results.add("收藏的歌单");
                    results.addAll(netPlaylists);
                }

                mAdapter.updateResults(results, playlists, netPlaylists);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (mContext == null)
                    return;
                mAdapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
            }
        }.execute();
    }

    @Override
    public void changeTheme() {
        super.changeTheme();
        swipeRefresh.setColorSchemeColors(ThemeUtils.getColorById(mContext,R.color.theme_color_primary));
    }
}
