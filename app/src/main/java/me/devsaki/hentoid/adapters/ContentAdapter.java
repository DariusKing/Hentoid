package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.ItemClickListener;
import me.devsaki.hentoid.listener.ItemClickListener.ItemSelectListener;
import me.devsaki.hentoid.services.DownloadService;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by avluis on 04/23/2016.
 * RecyclerView based Content Adapter
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentHolder> {

    private static final int VISIBLE_THRESHOLD = 10;
    private final Context context;
    private final SparseBooleanArray selectedItems;
    private final ItemSelectListener listener;
    private ContentsWipedListener contentsWipedListener;
    private EndlessScrollListener endlessScrollListener;
    private List<Content> contents = new ArrayList<>();

    public ContentAdapter(Context context, List<Content> contents, ItemSelectListener listener) {
        this.context = context;
        this.contents = contents;
        this.listener = listener;

        selectedItems = new SparseBooleanArray();
    }

    public void setEndlessScrollListener(EndlessScrollListener listener) {
        this.endlessScrollListener = listener;
    }

    public void setContentsWipedListener(ContentsWipedListener listener) {
        this.contentsWipedListener = listener;
    }

    private void toggleSelection(int pos) {
        if (selectedItems.get(pos, false)) {
            selectedItems.delete(pos);
            Timber.d("Removed item: %s", pos);
        } else {
            selectedItems.put(pos, true);
            Timber.d("Added item: %s", pos);
        }
        notifyItemChanged(pos);
    }

    public void clearSelections() {
        selectedItems.clear();
        notifyDataSetChanged();
    }

    private int getSelectedItemCount() {
        return selectedItems.size();
    }

    private List<Integer> getSelectedItems() {
        List<Integer> items = new ArrayList<>(selectedItems.size());
        for (int i = 0; i < selectedItems.size(); i++) {
            items.add(selectedItems.keyAt(i));
        }

        return items;
    }

    private boolean getSelectedItem(int item) {
        for (int i = 0; i < selectedItems.size(); i++) {
            if (selectedItems.keyAt(i) == item) {
                return selectedItems.get(item);
            }
        }

        return false;
    }

    public void setContentList(List<Content> contentList) {
        this.contents = contentList;
        updateContentList();
    }

    public void updateContentList() {
        this.notifyDataSetChanged();
    }

    @Override
    public ContentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_download, parent, false);
        return new ContentHolder(view);
    }

    @Override
    public void onBindViewHolder(ContentHolder holder, final int pos) {
        Content content = contents.get(pos);

        updateLayoutVisibility(holder, content, pos);
        populateLayout(holder, content, pos);
        attachOnClickListeners(holder, content, pos);
    }

    private void updateLayoutVisibility(ContentHolder holder, Content content, int pos) {
        if (pos == getItemCount() - VISIBLE_THRESHOLD && endlessScrollListener != null) {
            endlessScrollListener.onLoadMore();
        }

        if (getSelectedItems() != null) {
            int itemPos = holder.getLayoutPosition();
            boolean selected = getSelectedItem(itemPos);

            if (getSelectedItem(itemPos)) {
                holder.itemView.setSelected(selected);
            } else {
                holder.itemView.setSelected(false);
            }
        }

        final RelativeLayout items = holder.itemView.findViewById(R.id.item);
        LinearLayout minimal = holder.itemView.findViewById(R.id.item_minimal);

        if (holder.itemView.isSelected()) {
            Timber.d("Position: %s %s is a selected item currently in view.", pos, content.getTitle());

            if (getSelectedItemCount() >= 1) {
                items.setVisibility(View.GONE);
                minimal.setVisibility(View.VISIBLE);
            }
        } else {
            items.setVisibility(View.VISIBLE);
            minimal.setVisibility(View.GONE);
        }
    }

    private void populateLayout(ContentHolder holder, final Content content, int pos) {
        attachTitle(holder, content);
        attachCover(holder, content);
        attachSeries(holder, content);
        attachArtist(holder, content);
        attachTags(holder, content);
        attachSite(holder, content, pos);
    }

    private void attachTitle(ContentHolder holder, Content content) {
        CharSequence title;
        if (content.getTitle() == null) {
            title = context.getText(R.string.work_untitled);
        } else {
            title = content.getTitle();
        }

        holder.tvTitle.setText(title);
        if (holder.itemView.isSelected()) {
            holder.tvTitle2.setText(title);
        }
    }

    private void attachCover(ContentHolder holder, Content content) {
        // The following is needed due to RecyclerView recycling layouts and
        // Glide not considering the layout invalid for the current image:
        // https://github.com/bumptech/glide/issues/835#issuecomment-167438903
        holder.ivCover.layout(0, 0, 0, 0);
        holder.ivCover2.layout(0, 0, 0, 0);

        RequestOptions myOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder);

        Glide.with(context.getApplicationContext())
                .load(FileHelper.getThumb(content))
                .apply(myOptions)
                .transition(withCrossFade())
                .into(holder.ivCover);

        if (holder.itemView.isSelected()) {
            Glide.with(context.getApplicationContext())
                    .load(FileHelper.getThumb(content))
                    .apply(myOptions)
                    .transition(withCrossFade())
                    .into(holder.ivCover2);
        }
    }

    private void attachSeries(ContentHolder holder, Content content) {
        String templateSeries = context.getResources().getString(R.string.work_series);
        StringBuilder seriesBuilder = new StringBuilder();
        List<Attribute> seriesAttributes = content.getAttributes().get(AttributeType.SERIE);
        if (seriesAttributes == null) {
            holder.tvSeries.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < seriesAttributes.size(); i++) {
                Attribute attribute = seriesAttributes.get(i);
                seriesBuilder.append(attribute.getName());
                if (i != seriesAttributes.size() - 1) {
                    seriesBuilder.append(", ");
                }
            }
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
        holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@", seriesBuilder.toString())));

        if (seriesAttributes == null) {
            holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@",
                    context.getResources().getString(R.string.work_untitled))));
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
    }

    private void attachArtist(ContentHolder holder, Content content) {
        String templateArtist = context.getResources().getString(R.string.work_artist);
        StringBuilder artistsBuilder = new StringBuilder();
        List<Attribute> artistAttributes = content.getAttributes().get(AttributeType.ARTIST);
        if (artistAttributes == null) {
            holder.tvArtist.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < artistAttributes.size(); i++) {
                Attribute attribute = artistAttributes.get(i);
                artistsBuilder.append(attribute.getName());
                if (i != artistAttributes.size() - 1) {
                    artistsBuilder.append(", ");
                }
            }
            holder.tvArtist.setVisibility(View.VISIBLE);
        }
        holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@", artistsBuilder.toString())));

        if (artistAttributes == null) {
            holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@",
                    context.getResources().getString(R.string.work_untitled))));
            holder.tvArtist.setVisibility(View.VISIBLE);
        }
    }

    private void attachTags(ContentHolder holder, Content content) {
        String templateTags = context.getResources().getString(R.string.work_tags);
        StringBuilder tagsBuilder = new StringBuilder();
        List<Attribute> tagsAttributes = content.getAttributes().get(AttributeType.TAG);
        if (tagsAttributes != null) {
            for (int i = 0; i < tagsAttributes.size(); i++) {
                Attribute attribute = tagsAttributes.get(i);
                if (attribute.getName() != null) {
                    tagsBuilder.append(templateTags.replace("@tag@", attribute.getName()));
                    if (i != tagsAttributes.size() - 1) {
                        tagsBuilder.append(", ");
                    }
                }
            }
        }
        holder.tvTags.setText(Helper.fromHtml(tagsBuilder.toString()));
    }

    private void attachSite(ContentHolder holder, final Content content, int pos) {
        if (content.getSite() != null) {
            int img = content.getSite().getIco();
            holder.ivSite.setImageResource(img);
            holder.ivSite.setOnClickListener(v -> {
                if (getSelectedItemCount() >= 1) {
                    clearSelections();
                    listener.onItemClear(0);
                }
                Helper.viewContent(context, content);
            });
        } else {
            holder.ivSite.setImageResource(R.drawable.ic_stat_hentoid);
        }

        if (content.getStatus() != null) {
            StatusContent status = content.getStatus();
            int bg;
            switch (status) {
                case DOWNLOADED:
                    bg = R.color.card_item_src_normal;
                    break;
                case MIGRATED:
                    bg = R.color.card_item_src_migrated;
                    break;
                default:
                    Timber.d("Position: %s %s - Status: %s", pos, content.getTitle(), status);
                    bg = R.color.card_item_src_other;
                    break;
            }
            holder.ivSite.setBackgroundColor(ContextCompat.getColor(context, bg));

            if (status == StatusContent.ERROR) {
                holder.ivError.setVisibility(View.VISIBLE);
                holder.ivError.setOnClickListener(v -> {
                    if (getSelectedItemCount() >= 1) {
                        clearSelections();
                        listener.onItemClear(0);
                    }
                    downloadAgain(content);
                });
            } else {
                holder.ivError.setVisibility(View.GONE);
            }

        } else {
            holder.ivSite.setVisibility(View.GONE);
        }
    }

    private void attachOnClickListeners(final ContentHolder holder, Content content, int pos) {
        holder.itemView.setOnClickListener(new ItemClickListener(context, content, pos, listener) {

            @Override
            public void onClick(View v) {
                if (getSelectedItems() != null) {
                    int itemPos = holder.getLayoutPosition();
                    boolean selected = getSelectedItem(itemPos);
                    boolean selectionMode = getSelectedItemCount() > 0;

                    if (selectionMode) {
                        Timber.d("In Selection Mode - ignore open requests.");
                        if (selected) {
                            Timber.d("Item already selected, remove it.");

                            toggleSelection(itemPos);
                            setSelected(false, getSelectedItemCount());
                        } else {
                            Timber.d("Item not selected, add it.");

                            toggleSelection(itemPos);
                            setSelected(true, getSelectedItemCount());
                        }
                        onLongClick(v);
                    } else {
                        Timber.d("Not in selection mode, opening item.");

                        clearSelections();
                        setSelected(false, 0);

                        super.onClick(v);
                    }
                }
            }
        });

        holder.itemView.setOnLongClickListener(new ItemClickListener(context, content, pos, listener) {

            @Override
            public boolean onLongClick(View v) {
                if (getSelectedItems() != null) {

                    int itemPos = holder.getLayoutPosition();
                    boolean selected = getSelectedItem(itemPos);

                    if (selected) {
                        Timber.d("Item already selected, remove it.");

                        toggleSelection(itemPos);
                        setSelected(false, getSelectedItemCount());
                    } else {
                        Timber.d("Item not selected, add it.");

                        toggleSelection(itemPos);
                        setSelected(true, getSelectedItemCount());
                    }

                    super.onLongClick(v);

                    return true;
                }

                return false;
            }
        });
    }

    private void downloadAgain(final Content item) {
        int images;
        int imgErrors = 0;

        images = item.getImageFiles().size();

        for (ImageFile imgFile : item.getImageFiles()) {
            if (imgFile.getStatus() == StatusContent.ERROR) {
                imgErrors++;
            }
        }

        String message = context.getString(R.string.download_again_dialog_message).replace(
                "@error", imgErrors + "").replace("@total", images + "");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.download_again_dialog_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            HentoidDB db = HentoidDB.getInstance(context);

                            item.setStatus(StatusContent.DOWNLOADING);
                            item.setDownloadDate(new Date().getTime());

                            db.updateContentStatus(item);

                            Intent intent = new Intent(Intent.ACTION_SYNC, null, context,
                                    DownloadService.class);
                            context.startService(intent);

                            Helper.toast(context, R.string.add_to_queue);
                            removeItem(item);
                            notifyDataSetChanged();
                        })
                .setNegativeButton(android.R.string.no, null)
                .create().show();
    }

    private void shareContent(final Content item) {
        String url = item.getGalleryUrl();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setData(Uri.parse(url));
        intent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, url);
        intent.setType("text/plain");

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)));
    }

    private void archiveContent(final Content item) {
        Helper.toast(R.string.packaging_content);
        FileHelper.archiveContent(context, item);
    }

    private void deleteContent(final Content item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.ask_delete)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            clearSelections();
                            deleteItem(item);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> {
                            clearSelections();
                            listener.onItemClear(0);
                        })
                .create().show();
    }

    private void deleteContents(final List<Content> items) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.ask_delete_multiple)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            clearSelections();
                            deleteItems(items);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> {
                            clearSelections();
                            listener.onItemClear(0);
                        })
                .create().show();
    }

    @Override
    public long getItemId(int position) {
        return contents.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return contents.size();
    }

    public void sharedSelectedItems() {
        int itemCount = getSelectedItemCount();
        if (itemCount > 0) {
            if (itemCount == 1) {
                Timber.d("Preparing to share selected item...");

                List<Content> items;
                items = processSelection();

                if (!items.isEmpty()) {
                    shareContent(items.get(0));
                } else {
                    listener.onItemClear(0);
                    Timber.d("Nothing to share!!");
                }
            } else {
                // TODO: Implement multi-item share
                Timber.d("How even?");
                Helper.toast("Not yet implemented!!");
            }
        } else {
            listener.onItemClear(0);
            Timber.d("No items to share!!");
        }
    }

    public void purgeSelectedItems() {
        int itemCount = getSelectedItemCount();
        if (itemCount > 0) {
            if (itemCount == 1) {
                Timber.d("Preparing to delete selected item...");

                List<Content> items;
                items = processSelection();

                if (!items.isEmpty()) {
                    deleteContent(items.get(0));
                } else {
                    listener.onItemClear(0);
                    Timber.d("Nothing to delete!!");
                }
            } else {
                Timber.d("Preparing to delete selected items...");

                List<Content> items;
                items = processSelection();

                if (!items.isEmpty()) {
                    deleteContents(items);
                } else {
                    listener.onItemClear(0);
                    Timber.d("No items to delete!!");
                }
            }
        } else {
            listener.onItemClear(0);
            Timber.d("No items to delete!!");
        }
    }

    public void archiveSelectedItems() {
        int itemCount = getSelectedItemCount();
        if (itemCount > 0) {
            if (itemCount == 1) {
                Timber.d("Preparing to archive selected item...");

                List<Content> items;
                items = processSelection();

                if (!items.isEmpty()) {
                    archiveContent(items.get(0));
                } else {
                    listener.onItemClear(0);
                    Timber.d("Nothing to archive!!");
                }
            } else {
                // TODO: Implement multi-item archival
                Timber.d("How even?");
                Helper.toast("Not yet implemented!!");
            }
        } else {
            listener.onItemClear(0);
            Timber.d("No items to archive!!");
        }
    }

    private List<Content> processSelection() {
        List<Content> selectionList = new ArrayList<>();
        List<Integer> selection = getSelectedItems();
        Timber.d("Selected items: %s", selection);

        for (int i = 0; i < selection.size(); i++) {
            selectionList.add(i, contents.get(selection.get(i)));
            Timber.d("Added: %s to list.", contents.get(selection.get(i)).getTitle());
        }

        return selectionList;
    }

    private void removeItem(Content item) {
        removeItem(item, true);
    }

    private void removeItem(Content item, boolean broadcast) {
        int position = contents.indexOf(item);
        Timber.d("Removing item: %s from adapter.", item.getTitle());
        contents.remove(position);
        notifyItemRemoved(position);

        if (contents != null) {
            if (contents.size() == 0) {
                contentsWipedListener.onContentsWiped();
            }
            if (broadcast) {
                listener.onItemClear(0);
            }
        }
    }

    private void deleteItem(final Content item) {
        final HentoidDB db = HentoidDB.getInstance(context);
        removeItem(item);

        AsyncTask.execute(() -> {
            FileHelper.removeContent(item);
            db.deleteContent(item);
            Timber.d("Removed item: %s from db and file system.", item.getTitle());
        });

        notifyDataSetChanged();

        Helper.toast(context, context.getString(R.string.deleted).replace("@content", item.getTitle()));
    }

    private void deleteItems(final List<Content> items) {
        final HentoidDB db = HentoidDB.getInstance(context);
        for (int i = 0; i < items.size(); i++) {
            removeItem(items.get(i), false);
        }

        AsyncTask.execute(() -> {
            for (Content item : items) {
                FileHelper.removeContent(item);
                db.deleteContent(item);
                Timber.d("Removed item: %s from db and file system", item.getTitle());
            }
        });

        listener.onItemClear(0);
        notifyDataSetChanged();

        Helper.toast(context, "Selected items have been deleted.");
    }

    public interface EndlessScrollListener {
        void onLoadMore();
    }

    public interface ContentsWipedListener {
        void onContentsWiped();
    }
}
