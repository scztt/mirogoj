MMixer {
	var window, <faders, <rerouteSynth, <server, levelsBus,
		saveName, saveButton, popupList, <presets, fadetime=10, masterVolume, 
		cmdDown=false;
	
	*new {
		| rerouteSynth, ampBus, server |
		^super.new.init( rerouteSynth, ampBus, server );
	}
	
	init {
		| rs, bus, s |
		var faderNumber, groupSizes, xPos = 5, r;
		var spec = ControlSpec( -60, 6, \db, default:0, units:"dB" );
		this.loadPresets();	
		rerouteSynth = rs;
		levelsBus = bus;
		server = s;
				
		window = SCWindow( "Le Grande Mixer" );
	
		popupList = SCPopUpMenu( window, Rect( 5, 5, 100, 22 ))
			.action_({
				| v |
				this.applyPreset( presets[ v.items[ v.value ].asString ] );
				saveName.string_( v.items[ v.value ].asString );
			});
			
		saveName = SCTextField( window, Rect( 200, 9, 60, 18));
		saveButton = SCButton( window, Rect( 265, 5, 30, 22) )
			.states_([["save", Color.black, Color.blue(0.7)]])
			.action_({
				this.presets[ saveName.string ] = faders.collect( _.value );
				this.savePresets();
				this.updatePopupList();
			});
		fadetime = SCNumberBox( window, Rect( 300, 9, 60, 18))
			.value_(10);
			
		this.updatePopupList();
		
		faders = List.new;
		groupSizes = [ 4, 4, 4, 3, 4, 1, 3, 4, 1, 4, 4, 4 ];
		faderNumber = 0;
		groupSizes.do({
			| size, i |
			var group = List.new;
			size.do({
				group.add( MFader( window, 
							Rect( xPos, 35, 20, 200 ), 
							rerouteSynth, levelsBus, faderNumber, 0, server ) );
				xPos = xPos + 23;
				faderNumber = faderNumber + 1;
			});
			faders.addAll( group );

			SCButton( window, Rect( xPos-(size*23), 236, size*23-3, 16 ) )
				.states_([["( )",Color.black, Color.grey],["(*)", Color.black, Color.green]])
				.action_({
					| v |
					(v.value==1).if({
						group.do({
							| fader |
							fader.linked = group;
						})
					},{
						group.do( _.linked_(nil) );
					})
				})
				.font_( Font("Verdana",7) );
			
			xPos = xPos + 5;
		});
		masterVolume = SCSlider( window, Rect( xPos, 45, 20, 190 ))
			.background_( Color.new(0.9, 0.6, 0.6).alpha_(0.7))
			.value_(1)
			.action_({
				|v|
				s.sendMsg(\n_set, rerouteSynth.nodeID, \masterAmp, spec.map( v.value ).dbamp );
			});	
//		window.bounds = window.bounds.width_( masterVolume.bounds.right + 5 );
		window.drawHook_({
				Pen.line( 5@84.5, window.bounds.width-5@84.5 );
				Pen.stroke;
			});
		r = Rect(0,0,0,0);
		window.view.children.do({
			| child |
			r = r.union( child.bounds )
		});
		window.bounds_( r.resizeBy(5,5) );
		window.front;
	}
	
	savePresets {
		var path = "~/Library/Application Support/SuperCollider/Extensions/mirogoj/data/mixerPresets.txt".standardizePath;
		presets.writeArchive( path );
	}

	loadPresets {
		var path = "~/Library/Application Support/SuperCollider/Extensions/mirogoj/data/mixerPresets.txt".standardizePath;
		File.exists( path ).if({
			{
				^presets = Dictionary.readArchive( path );
			}
		});
		presets = Dictionary.new();
		presets[ "default" ] = 0!40;
		presets[ "test" ] = -3!40;
		presets.writeArchive( path );
	}	

	applyPreset {
		| preset |
		var newVals, oldVals;
		oldVals = faders.collect({ |f|  f.value.dbamp });
		newVals = preset.collect( _.dbamp );
		{
			ReplaceOut.kr( levelsBus.index, newVals.size.collect({ |i|
				Line.kr( oldVals[i], newVals[i], fadetime.value, doneAction:2 )
			}) );
		}.play();
		
		faders.do({
			| fader, i |
			fader.value_( preset[i], true );
			fader.update();
		})
	}

	updatePopupList {
		popupList.items_( presets.keys.asArray );
	}
}

MFader {
	classvar emptyArray;
	var server, window, <bounds, node, <bus, num, <value,
		slider, display, spec, linked=false, 
		<linked, updateBundle;
	
	*classInit {
		emptyArray = nil!20;
	}
	
	*new {
		| window, rect, node, bus, num, value=0, server |
		^super.new.init( window, rect, node, bus, num, value, server)
	}
	
	init {
		| ...args |
		#window, bounds, node, bus, num, value, server = args;
		spec = ControlSpec( -30, 3, \db, default:value, units:"dB" );
		slider = SCSlider( window, bounds.copy.top_(bounds.top+15).height_(bounds.height-15) )
			.action_({
				| v |
				this.updateLinked( spec.map( v.value ) - value );
				this.value_( spec.map( v.value ) );
				this.updateDisplay();
			});
		slider.resize(5);
		display = SCStaticText( window, bounds.copy.height_(15) )
			.font_( Font("Verdana", 8) )
			.align_(\center);
					
		this.value_( value );
		this.update();
		linked = List.new;
	}

	value_{
		| val, noMsg=false |
		value = val;
		if( noMsg.not, {
			this.sendMessage()
		});
	}
	
	relativeValue_{
		|offset|
		this.value = value + offset;
	}
	
	update {
		this.updateDisplay();
		this.updateSlider();
	}
	
	updateDisplay {
		display.string = value.round(0.1).asString;
	}
	
	updateSlider {
		slider.value = spec.unmap( value );
	}
	
	linked_{
		| list |
		(linked = list.copy).remove( this );
		linked
	}
	
	updateLinked {
		| offset |
		linked.do({
			| l |
			l.relativeValue_( offset );
			l.update();
		})
	}
	
	sendMessage {
		server.listSendMsg([ \c_set, bus.index + num, value.dbamp ]);
	}
}