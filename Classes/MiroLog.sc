MiroLog {
	classvar <logWindows, <logStrings, <logFormatters;
	classvar <clock;
	
	*initClass {
		logWindows = IdentityDictionary.new;
		logStrings = IdentityDictionary.new;
		logFormatters = IdentityDictionary.new;
		logFormatters[ \levels ] = {
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
		var refresher;
		logWindows[ name ].isNil.if({
			refresher = Routine({
				while( logWindows[ name ].notNil, {
					
				})
			});
			logWindows[ name ] = Document.new( name.asString() )
					.background_(Color.grey(0.8).alpha_(0.95))
					.bounds_(Rect(10,SCWindow.screenBounds.height-10-500,300,500))
					.string_( this.getString(name) )
				.onClose_({ |d| logWindows[ name ] = nil });
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
		var logWindow, logString, formatter;
		{
			logWindow = logWindows[ name ];
			
			formatter = logFormatters[ name ].notNil.if({
				string = logFormatters[ name ].value( string );
				if( string.size > 1000000, {
					string = string.copyRange( string.size-50000, string.size );
					logWindow.notNil.if({ logWindow.string_(string) });	
				})
			});
			if( logWindow.notNil, {
				logWindow.insertTextRange("\n" + string + "\n", 999999999999, 1);
				logWindow.selectRange(logWindow.string.size);
			},{
			logStrings[ name ] = (logStrings[name] ? "") + "\n" + string;
			})
		}.defer;	
	}
}

+String {
	log {
		| name=\default, indent=0 |
		MiroLog.postln( name, ("\t" ! indent).join ++ this )
	}
}

+Object {
	log {
		| name, indent |
		this.asString.log(name, indent)
	}
}