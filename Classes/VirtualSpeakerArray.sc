VirtualSpeakerArray {
	classvar singleton;
	var <speakers, playing=false, <>server, <listenerBus, <listenerPositionBus, <group;
	var <listenerSynth, <groupings, <groupColors;
	var <listenerPos, <listenerAngle;
	var <>speakerEffectsDef, <>speakerEffectsSynth;
	var walkTestRoutines, walkSynth;
	
	*get {
		^singleton
	}
	
	*listenerSynthDef {
		^SynthDef( \uhjListener, {
			| out=0, inBus, amp=1 |
			var w, x, y;
			SeedMaster.ugen;
			#w,x,y = In.ar( inBus, 3 )*amp;
			Out.ar( out, B2UHJ.ar(w, x, y) );
		})
	}

	*newEmpty {
		| server |
		^(singleton = super.new.init(server));
	}
	
	*newFromDataArray {
		| server, data |
		^(singleton = super.new.initWithDataArray( server, data ));
	}
	
	*newFromFile {
		| server, filename |
		var file, data;
		filename = filename ? Platform.userAppSupportDir +/+ "extensions/mirogoj/data/miroArrangement1.txt";
		file =  File( filename.postln, "r");
		data = file.readAllString().interpret;
		file.close();
		^(singleton = super.new.initWithDataArray( server, data));
		
	}
	
	*walkSynthDef {
		^SynthDef( \walk, {
			| listenerPositionBus, times |
			var x, y, direction, oldX, oldY, xEnv, yEnv;
			#oldX, oldY = In.kr( listenerPositionBus, 2 );
			xEnv = Env.newClear(10);
			yEnv = Env.newClear(10);
			x = EnvGen.kr( Control.names([\xEnv]).kr( xEnv.asArray ), 
				1, doneAction:2 );
			y = EnvGen.kr( Control.names([\yEnv]).kr( yEnv.asArray ), 1 );
			direction = Slew.kr( ((x@y) - (oldX@oldY)).theta/pi/2, 0.5, 0.5) + 0.25;
			Out.kr( listenerPositionBus, [x, y, direction] );
		})
	}
	
	init {
		| server |
		CmdPeriod.add( this );
		speakers = List.new;
		groupings = Dictionary.new;
		this.server = server;
		
		//////////////////////////////////////////////////////////////////////////////
		// 4 channel b-format signal that can be mixed down as needed for previewing
		// and 3 channel (x, y, and rotation) control signal for the listener.
		listenerBus = CtkAudio( 4, server:server );
		listenerPositionBus = CtkControl( 3, server:server );
		
		// Assumes group manager == g, this will change
		group = GroupManager.get(server)[\speakers];
		listenerSynth = CtkNoteObject( this.class.listenerSynthDef ).new( server:server, addAction:\after, target:GroupManager.get(server)[\listener] );
		
		listenerSynth.inBus = listenerBus.bus;

		listenerPos = 0@0;
		listenerAngle = 0;
		
		walkTestRoutines = List.new();
	}
	
	doOnCmdPeriod {
		this.free;
	}
	
	initWithDataArray {
		| server, data |
		var newSpeaker, groups;
	
		this.init( server );
	
		groups = IdentityDictionary.new;
		
		data[\speakers].do({
			| speakerData, i |
			this.createSpeaker( speakerData[\x], speakerData[\y], 
				speakerData[\angle], speakerData[\amp] );
			if( speakerData[\group].notNil, {
				groups[ speakerData[\group] ].isNil.if({
					groups[ speakerData[\group] ] = List.new;
				});
				groups[ speakerData[\group] ].add(i)
			});
		});
		
		if( data[\groupings].notNil, {
			groups  = data[\groupings];
		});
		
		groups.keysValuesDo({
			| key, value |
			this.createGrouping( key, value.collect( speakers[_] ) );
		});
	}
	
	amp_{
		|val=1|
		//this.listenerSynth.set( 0, \amp, val )
		server.sendMsg( \n_set, this.listenerSynth.node, \amp, val );
	}
	
	listenerPos_{
		| position |
		listenerPos = position;
		listenerPositionBus.set([ position.x, position.y, listenerAngle ]);
		this.changed( \listener );
	}
	
	listenerAngle_{
		| angle |
		listenerAngle = angle;
		listenerPositionBus.set([ listenerPos.x, listenerPos.y, listenerAngle ]);
		this.changed( \listener );
	}
	
	serialize {
		^Dictionary[
			\speakers ->
				speakers.collect({
					| speaker |
					speaker.serialize();
				}),
			\groupings -> this.serializeGroupings
		];		
	}
	
	serializeGroupings {
		var serialized = Dictionary.new;
		groupings.keysValuesDo({
			| key, value |
			serialized[key] = value.collect( speakers.indexOf(_) )
		});
		^serialized;
	}
	
	play {
		var def;
		if( playing.not, {
			if( speakerEffectsDef.notNil, {
				def = SynthDef( \speakerfx, {
					SeedMaster.ugen;
					ReplaceOut.ar( 
						this.getBusNums( this.getBusNums() ),
						speakerEffectsDef.value( In.ar( this.getBusNums() ) )
					)
				});
				def.inspect;
				speakerEffectsSynth = CtkNoteObject( def, server:server )
					.new( target:GroupManager.manager[\speakerfx] )
					.play();
			});
			listenerPositionBus.play;
			server.makeBundle( nil, {
				speakers.do({
					| speaker |
					speaker.play();
				})
			});
			listenerSynth.play();	
			playing = true;
		})
	}
	
	free {
		if( playing, {
			server.makeBundle( nil, {
				speakers.do({
					| speaker |
					speaker.free();
				});
				if( speakerEffectsSynth.notNil, { speakerEffectsSynth.free });
				listenerSynth.free;
			});
			playing = false;
		})		
	}
	
	createGrouping {
		| name, speakers |
		groupings[name] = speakers;
	}
	
	getBusses {
		^speakers.collect( _.inBus )
	}

	getBusNums {
		^speakers.collect({ |spk|  spk.inBus.bus })
	}
	
	createSpeaker {
		| x=0, y=0, angle=0, amp=0 |
		var speaker;
		speaker = VirtualSpeaker.new( server, group, x, y, angle, amp, CtkAudio( server:server ), 
			listenerBus, listenerPositionBus );
		if( playing, { speaker.play });
		speakers.add( speaker );
		this.changed( \speakers );
		^speaker
	}
	
	playTestSequence {
		| time=5 |
		Routine({
			if( playing.not, { this.play });
			server.sync();
		}).play;
		Routine({
			speakers.do({
				|speaker, i|
				speaker.test( time*0.8 );
				time.yield;
			})
		}).play;
	}
	
	walkTest {
		| pointArray, speed=0.3 |
		var xEnv, yEnv, times, lastPos, totalTime;
		var listenerPositionGetableBus;
		
		if( walkTestRoutines.isEmpty.not, {
			this.clearWalkTest();
		});
		
		listenerPositionGetableBus = Bus( \control, listenerPositionBus.bus, 3);
		speed = speed*4; 
		xEnv = [];
		yEnv = [];
		times = [];
		lastPos = listenerPos;
		xEnv = xEnv.add( lastPos.x ); yEnv = yEnv.add( lastPos.y );
		
		pointArray.do({
			| pos |
			xEnv = xEnv.add( pos.x ); yEnv = yEnv.add( pos.y );
			times = times.add( ( pos - lastPos ).rho / speed );
			lastPos = pos;
		});
		totalTime = times.sum;
		xEnv = Env( xEnv, times );
		yEnv = Env( yEnv, times );
		if( playing, {
			walkTestRoutines.add( Routine({
				this.class.walkSynthDef.send(server);
				server.sync();
				walkSynth = Synth(\walk, [ \listenerPositionBus, listenerPositionBus.bus,
					  \xEnv, xEnv, \yEnv, yEnv, \times, times ], group, \addToHead );
				walkSynth.setn( \xEnv, xEnv.asArray, \yEnv, yEnv.asArray );
			}).play );
			walkTestRoutines.add( Routine({
				(totalTime*5).do({
					listenerPositionGetableBus.getn(3, { 
						| values |
						listenerPos.x = values[0];
						listenerPos.y = values[1];
						listenerAngle = values[2];
						this.changed( \listener );
					});
					0.2.yield;
				})
			}).play );
		})
	}
	
	clearWalkTest {
		walkTestRoutines.do( _.stop );
		walkTestRoutines = List.new;
		walkSynth.free;
	}
	
	testSpeaker {
		| i |
		var speaker, dist, speakerAngle;
		speaker = speakers[i];
		speaker.test(4);

		postln( "speaker number = " + i);
		postln( "speaker angle = " + speaker.angle );
		postln( "listener angle = " + listenerAngle );
		
		speakerAngle = VirtualSpeaker.calcSpeakerAngle( 
				listenerPos.x, listenerPos.y, speaker.x, speaker.y, speaker.angle  );
		postln( "listener angle relative to speaker = " + speakerAngle );
		
		postln( "speaker angle relative to listener = " + VirtualSpeaker.calcRelativeAngle( 
				listenerPos.x, listenerPos.y, speaker.x, speaker.y, listenerAngle ) );
		
		postln( "angleAmp = " + VirtualSpeaker.calcAmpFromAngle( speakerAngle ) );
	}
}