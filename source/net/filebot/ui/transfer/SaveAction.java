package net.filebot.ui.transfer;

import static net.filebot.UserFiles.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import net.filebot.ResourceManager;

public class SaveAction extends AbstractAction {

	public static final String EXPORT_HANDLER = "exportHandler";

	public SaveAction(FileExportHandler exportHandler) {
		this("Save as ...", ResourceManager.getIcon("action.save"), exportHandler);
	}

	public SaveAction(String name, Icon icon, FileExportHandler exportHandler) {
		putValue(NAME, name);
		putValue(SMALL_ICON, icon);
		putValue(EXPORT_HANDLER, exportHandler);
	}

	public FileExportHandler getExportHandler() {
		return (FileExportHandler) getValue(EXPORT_HANDLER);
	}

	protected boolean canExport() {
		return getExportHandler().canExport();
	}

	protected void export(File file) throws IOException {
		getExportHandler().export(file);
	}

	protected File getDefaultFile() {
		return new File(validateFileName(getExportHandler().getDefaultFileName()));
	}

	public void actionPerformed(ActionEvent evt) {
		try {
			if (canExport()) {
				File file = showSaveDialogSelectFile(false, getDefaultFile(), (String) getValue(Action.NAME), evt.getSource());

				if (file != null) {
					export(file);
				}
			}
		} catch (Exception e) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, e.toString(), e);
		}
	}
}
