package stasis.client_android.activities.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView

class ExpandingListView : ListView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE shr 2, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, heightSpec)
        layoutParams.height = measuredHeight
    }

    override fun addView(child: View?, index: Int) {}
    override fun removeView(child: View?) {}
}
