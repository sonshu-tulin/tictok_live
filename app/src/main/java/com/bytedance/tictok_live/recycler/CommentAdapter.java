package com.bytedance.tictok_live.recycler;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.tictok_live.R;
import com.bytedance.tictok_live.model.Comment;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论适配器，解析公屏评论信息
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentViewHolder> {
    public static final String TAG = "CommentAdapter";

    private List<Comment> commentList;

    // 构造方法：接收数据集
    public CommentAdapter(List<Comment> commentList){
        this.commentList = commentList;
    }


    /**
     * 第一步：Item创建ViewHolder（加载item布局）
     */
    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemRootView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment,parent,false);
        //创建返回的ViewHolder
        return new CommentViewHolder(itemRootView);
    }

    /**
     * 第二部：绑定数据到ViewHolder
     */
    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = commentList.get(position);
        holder.bindData(comment);
    }

    /**
     * 第三步：获取列表数量
     */
    @Override
    public int getItemCount() {
        return commentList != null ? commentList.size() : 0;
    }

    /**
     * 新增单条评论
     * @param newComment 新评论
     */
    public void addComment(Comment newComment) {
        commentList.add(newComment);
        notifyItemInserted(commentList.size() - 1);
    }


    /**
     * 设置评论数据
     * @param comments 评论列表
     */
    public void setData(List<Comment> comments) {
        commentList.clear(); // 先清空原有列表，避免重复
        if (comments == null) {
            commentList = new ArrayList<>();
        }
        commentList.addAll(comments);
        notifyDataSetChanged();
    }
}
