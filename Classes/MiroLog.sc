MiroLog {
	classvar <logWindows, <logStrings, <logFormatters, <defaultFormatter;
	classvar <clock;
	
	*initClass {
		logWindows = IdentityDictionary.new;
		logStrings = IdentityDictionary.new;
		logFormatters = IdentityDictionary.new;
		defaultFormatter = {
			| string |
			format("(%)\t%", this.getTime().asTimeString(1), string )
		};
		clock = AppClock.seconds;
	}
	
	*resetTime {
		clock = AppClock.seconds
	}
	
	*getTime {
		^(AppClock.seconds - clock).round(0.1)
	}
	
	*clear {
		| name=\default |
		logStrings[ name ] = "";
		logWindows[ name ].notNil.if({ logWindows[ name ].string_("") });
	}
	
	*getWindow {
		| name=\default |
		var refresher, newName;
		logWindows[ name ].isNil.if({
			newName = "Log:" + name.asString();
			logWindows[ name ] = Document.allDocuments.detect({ |d| d.name==newName }) ?? {
				Document.new( newName )
					.background_(Color.grey(0.8))
					.bounds_(Rect(100,100,300,500))
					.string_( this.getString(name) )
					.onClose_({ |d| logWindows[ name ] = nil })
				};
		});
		^logWindows[ name ].front
	}
	
	*getString {
		| name=\default |
		logStrings[ name ].isNil.if({
			logStrings[ name ] = ""
		});
		^logStrings[ name ]
	}
	
	*postln {
		| name=\default, string |
		var logWindow, formatter;
		
		logWindow = logWindows[ name ];
		formatter = logFormatters[ name ] ? defaultFormatter;
		string = formatter.value(string);
		if( logWindow.notNil, {
			logWindow.insertTextRange("\n" + string, 999999999999, 1);
			logWindow.selectRange(logWindow.string.size);
		});
		
		logStrings[ name ] = (logStrings[name] ? "") + "\n" + string;
	}
}

+String {
	log {
		| name=\default |
		{ MiroLog.postln( name, this ) }.defer;
	}
}