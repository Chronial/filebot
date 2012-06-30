// filebot -script "fn:sysinfo" -trust-script

// FileBot 2.62 (r993)
println net.sourceforge.filebot.Settings.applicationIdentifier

// MediaInfo: MediaInfoLib - v0.7.48
try {
	print 'MediaInfo: '
	println net.sourceforge.filebot.mediainfo.MediaInfo.version()
} catch(error) {
	println error.cause
}

// 7-Zip-JBinding: OK
try {
	print '7-Zip-JBinding: '
	new net.sourceforge.filebot.archive.Archive(null) // load 7-Zip-JBinding native libs
} catch(NullPointerException e) {
	println "OK"
} catch(Throwable error) {
	println error
}

// Java(TM) SE Runtime Environment 1.6.0_30 (headless)
println net.sourceforge.filebot.Settings.javaRuntimeIdentifier

// 32-bit Java HotSpot(TM) Client VM
println String.format('%d-bit %s', com.sun.jna.Platform.is64Bit() ? 64 : 32, _system['java.vm.name'])

// Windows 7 (x86)
println String.format('%s (%s)', _system['os.name'], _system['os.arch'])