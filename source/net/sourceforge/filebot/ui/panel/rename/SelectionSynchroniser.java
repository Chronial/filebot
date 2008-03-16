
package net.sourceforge.filebot.ui.panel.rename;


import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


class SelectionSynchroniser {
	
	private JList list1;
	private JList list2;
	
	private SelectionSynchronizeListener selectionSynchronizeListener1;
	private SelectionSynchronizeListener selectionSynchronizeListener2;
	
	
	public SelectionSynchroniser(JList list1, JList list2) {
		this.list1 = list1;
		this.list2 = list2;
		
		selectionSynchronizeListener1 = new SelectionSynchronizeListener(list2);
		selectionSynchronizeListener2 = new SelectionSynchronizeListener(list1);
		
		setEnabled(true);
	}
	

	public void setEnabled(boolean enabled) {
		// remove listeners to avoid adding them multiple times
		list1.removeListSelectionListener(selectionSynchronizeListener1);
		list2.removeListSelectionListener(selectionSynchronizeListener2);
		
		// if enabled add them again
		if (enabled) {
			list1.addListSelectionListener(selectionSynchronizeListener1);
			list2.addListSelectionListener(selectionSynchronizeListener2);
		}
	}
	
	
	private static class SelectionSynchronizeListener implements ListSelectionListener {
		
		private JList target;
		
		
		public SelectionSynchronizeListener(JList to) {
			this.target = to;
		}
		

		public void valueChanged(ListSelectionEvent e) {
			JList source = (JList) e.getSource();
			int index = source.getSelectedIndex();
			
			if (target.getModel().getSize() > index) {
				if (index != target.getSelectedIndex()) {
					target.setSelectedIndex(index);
				}
				
				target.ensureIndexIsVisible(index);
			} else {
				target.clearSelection();
			}
		}
		
	}
	
}
