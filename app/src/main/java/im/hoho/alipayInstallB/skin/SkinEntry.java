package im.hoho.alipayInstallB.skin;

/**
 * UI 列表项数据：目录名 + 显示名 + 是否已选中。
 */
public final class SkinEntry {

    public final String dirName;
    public final String displayName;
    public final boolean selected;

    public SkinEntry(String dirName, String displayName, boolean selected) {
        this.dirName = dirName;
        this.displayName = displayName;
        this.selected = selected;
    }

    @Override
    public String toString() {
        return displayName + " (" + dirName + ")";
    }
}
