package com.bytedance.tictok_live.viewholder;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.tictok_live.R;
import com.bytedance.tictok_live.model.Comment;

/**
 * 绑定布局控件
 */
public class CommentViewHolder extends RecyclerView.ViewHolder {

    ImageView ivCommentAvatar;
    TextView tvCommentName;
    TextView tvCommentContent;

    //绑定控件
    public CommentViewHolder(@NonNull View itemView) {
        super(itemView);
        ivCommentAvatar = itemView.findViewById(R.id.iv_comment_avatar);
        tvCommentName = itemView.findViewById(R.id.tv_comment_name);
        tvCommentContent = itemView.findViewById(R.id.tv_comment_content);
    }

    //绑定数据
    public void bindData(Comment comment){
        Context context = itemView.getContext();

        Glide.with(context)
                .load(comment.getAvatar())
                .circleCrop()
                .error(R.mipmap.ic_launcher)
                .placeholder(R.mipmap.ic_launcher)
                .into(ivCommentAvatar);

        tvCommentName.setText(comment.getName());
        tvCommentContent.setText(comment.getComment());
    }
}
