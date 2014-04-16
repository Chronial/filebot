package net.sourceforge.filebot.cli;

import static net.sourceforge.filebot.Settings.*;
import static net.sourceforge.filebot.cli.CLILogging.*;
import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;

import java.io.Console;
import java.util.Map;
import java.util.logging.Logger;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import net.sourceforge.filebot.MediaTypes;
import net.sourceforge.filebot.format.AssociativeScriptObject;
import net.sourceforge.filebot.util.FileUtilities;

public abstract class ScriptShellBaseClass extends Script {

	public ScriptShellBaseClass() {
		System.out.println(this);
	}

	private Map<String, ?> defaultValues;

	public void setDefaultValues(Map<String, ?> values) {
		this.defaultValues = values;
	}

	public Map<String, ?> getDefaultValues() {
		return defaultValues;
	}

	@Override
	public Object getProperty(String property) {
		try {
			return super.getProperty(property);
		} catch (MissingPropertyException e) {
			// try user-defined default values
			if (defaultValues != null && defaultValues.containsKey(property)) {
				return defaultValues.get(property);
			}

			// can't use default value, rethrow exception
			throw e;
		}
	}

	public void include(String input) throws Throwable {
		try {
			executeScript(input, null);
		} catch (Exception e) {
			printException(e);
		}
	}

	public Object executeScript(String input) throws Throwable {
		return executeScript(input, null);
	}

	public Object executeScript(String input, Map<String, ?> bindings, Object... args) throws Throwable {
		// apply parent script defines
		Bindings parameters = new SimpleBindings();

		// initialize default parameter
		if (bindings != null) {
			parameters.putAll(bindings);
		}
		parameters.put(ScriptShell.ARGV_BINDING_NAME, FileUtilities.asFileList(args));

		// run given script
		ScriptShell shell = (ScriptShell) getBinding().getVariable(ScriptShell.SHELL_BINDING_NAME);
		return shell.runScript(input, parameters);
	}

	public Object tryQuietly(Closure<?> c) {
		try {
			return c.call();
		} catch (Exception e) {
			return null;
		}
	}

	public Object tryLoudly(Closure<?> c) {
		try {
			return c.call();
		} catch (Exception e) {
			printException(e);
			return null;
		}
	}

	public void printException(Throwable t) {
		CLILogger.severe(String.format("%s: %s", t.getClass().getSimpleName(), t.getMessage()));
	}

	public void die(String message) throws Throwable {
		throw new Exception(message);
	}

	// define global variable: _args
	public ArgumentBean get_args() {
		return getApplicationArguments();
	}

	// define global variable: _def
	public Map<String, String> get_def() {
		return getApplicationArguments().defines;
	}

	// define global variable: _system
	public AssociativeScriptObject get_system() {
		return new AssociativeScriptObject(System.getProperties());
	}

	// define global variable: _environment
	public AssociativeScriptObject get_environment() {
		return new AssociativeScriptObject(System.getenv());
	}

	// define global variable: _types
	public MediaTypes get_types() {
		return MediaTypes.getDefault();
	}

	// define global variable: _log
	public Logger get_log() {
		return CLILogger;
	}

	// define global variable: console
	public Console getConsole() {
		return System.console();
	}

	@Override
	public Object run() {
		return null;
	}

}
