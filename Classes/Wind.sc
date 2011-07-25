Wind {
	classvar notes, windEnvs, chooseEnv;
	var <server, <outbus, <group, <srcsynth, <routesynth, <srcBus, <routeBus, <sntask, <udtask, <twait, <voiceBus;
	var <upar, <lpar, gustWeights, data;
	var rateEnv, lpEnv, upEnv, waitEnv, lastDur;
	
	*initClass {
		
		notes = CtkProtoNotes(
				
			SynthDef(\grainFM, {arg duration, grainPeriod, grainMinFreq, grainMaxFreq, grainAmp, dexMax = 10,
				modrate=0.7, id = 1, seed = 123, out, gate = 1, grnrate;
				var grains, freq, trigger;
				SeedMaster.ugen;
				trigger = Dust.ar( Lag.kr(grnrate, 1.5) );
				freq = TRand.ar(grainMinFreq, grainMaxFreq, trigger);
				grains = GrainFM.ar(1, trigger, grainPeriod, 
					freq, freq * LFNoise1.kr(modrate).range(0.2, 2), 
					LFNoise1.kr.range(4, dexMax),
					0, -1, Lag.kr(grainAmp, 6),
					maxGrains:1024 );	
				Out.ar(out, grains);
				}),
						
			SynthDef(\shapeNote, {arg routeout = 0, inbus, freq, dur, rq = 0.0001, mul = 20, att, dec;
				var env, src, res;
				env = EnvGen.kr(Control.names([\amp]).kr(Env.newClear(5)), // 5 breakpoints
					timeScale: dur, doneAction:2);
				src = In.ar(inbus, 1);
				res = Formlet.ar(src, freq, att, dec, mul); 
				Out.ar( routeout, (res * env) );
				}),

			SynthDef( \router, { arg dirout=0, voiceout, inbus, voicemix=0, fade=12;
				var src, voicefade;
				src = In.ar(inbus, 1);
				voicefade = Lag.kr( voicemix, fade );
				Out.ar( dirout, src * (1 - voicefade) );
				Out.ar( voiceout, src * voicefade ); 
				})
	
			);
		
		windEnvs = Dictionary.new
			.add('perc' 	-> Env([0.0001, 1, 0.7, 0.0001], [0.1, 0.2, 0.7], \exp))
			.add('reverse'-> Env([0.0001, 0.7, 1, 0.0001], [0.65, 0.2, 0.15], [4, \welch, -3]))
			.add('swell'	-> Env([0.0001, 1, 1, 0.0001], [0.45, 0.1, 0.45], \sin))
			.add('wave'	-> Env([0.0001, 0.45, 0.1, 1, 0.0001], [0.15, 0.2, 0.4, 0.25], \sin))
			.add('shore'	-> Env([0.0001, 1, 0.15, 0.5, 0.0001], [0.25, 0.4, 0.15, 0.2], \sin))
			.add('u'		-> Env([0.0001, 1, 0.15, 1, 0.0001], [0.05, 0.4, 0.4, 0.15], \sin));

		chooseEnv = {arg weight; ['perc', 'reverse', 'swell', 'wave', 'shore', 'u'].wchoose(weight)};
	
	}
	
	*new { | server, outbus, data, grainrate=5000, lowerPartial=100, upperPartial=8000, wait=6, amp = 1 |
		^super.newCopyArgs( server, outbus ).init( grainrate, data, lowerPartial, upperPartial, wait, amp, outbus, server )
	}
	
	init { |  grainrate, argdata, lowerPartial, upperPartial, wait, amp, outbus, argserver |
	
		server = argserver ?? {Server.default};
		group = CtkGroup.play( server:server, addAction:\head );
		srcBus = CtkAudio.new( server: server );
		routeBus = CtkAudio.new( server: server );
		voiceBus = CtkAudio.new( server: server );
		
		srcsynth = notes[\grainFM].new(addAction: \head, target: group)
			.randSeed_(SeedMaster.seed)
			.out_(srcBus).grainPeriod_(0.001)
			.grainMinFreq_(100).grainMaxFreq_(8000)
			.grnrate_(grainrate).grainAmp_(amp)
			.play( group );
			
		routesynth = notes[\router].new(addAction: \tail, target: group)
			.inbus_( routeBus )
			.dirout_( outbus )
			.voiceout_( voiceBus )
			.play( group );
		
		upar = upperPartial;
		lpar = lowerPartial;
		gustWeights = [0.333, 0.333, 0.333];
		twait = wait;
		data = argdata;
			// shapenote task
		sntask = Task({
				(data.size - 1).do({arg i;
					var name, age, topPartial, gustEnv;

					//"*********".postln;				
					name = data[i][1]; 		// the name, already converted to relative occurences
					age = data[i][0]/63.2465; 	// age, as a ratio of the average age of THE DEAD						
						// scaling it by it's own max value, extending each name to top partial
					//name = name / name.maxItem; 
						// scaling the name by the max possible value for all names, name may not get to topPartial
					name = name / 11.96;	
					topPartial = upar * [1, -2.midiratio, -3.midiratio, -5.midiratio, -8.midiratio, -12.midiratio, -18.midiratio].choose;
					gustEnv = chooseEnv.value(gustWeights);
					//gustEnv.postln;
					if( name.notNil, {
						name.do({ | letter |
							var freq, dur;
								lastDur = 0; 
								freq = letter.linlin(0, 1, lpar, topPartial); // 11.96 is the max relative occurence
								//"this gust in Hz: ".post; freq.postln;
								dur = rrand(0.0, 1.0).linlin(0, 1, 3, 7+(age*9) ); // 7 is min, 7+9 is max
								(lastDur<dur).if({ lastDur = dur });
								notes[\shapeNote].new(addAction: \before, target: routesynth)
									.routeout_(routeBus).inbus_( srcBus )
									.amp_( windEnvs.at(gustEnv) )
									.freq_( freq ).dur_( dur )
									.att_( rrand(0.01, 0.0075) ).dec_( rrand(0.005, 0.001) )
									.mul_( rrand(0.01, 0.1) ).play( group );	
							});
						name.maxItem.linlin(0, 1, 1, rrand(0.5, twait)).wait;
					});
				});	
		});

			// update task
		udtask = Task({
			| starttime |
			var step;
			step = 0.75;			
			inf.do({ 
				var now, newrate;
				
				now = thisThread.beats - starttime;
				//"now: ".post; now.postln;
				
				upar = upEnv[ now ];
				lpar = lpEnv[ now ];
				newrate = rateEnv[ now ];
				srcsynth.grnrate_( newrate );
				twait = waitEnv[ now ];
//				"upar =   ".post; upar.postln;
//				"lpar = 	 ".post; lpar.postln;
//				"currate = ".post; srcsynth.grnrate.postln;
//				"twait = ".post; twait.postln;
				
				step.wait;
				})			
		});
		
		CmdPeriod.add({ group.deepFree })
	}
	
	play {
		sntask.play;
		}
	
	stop	{ sntask.stop }
	
	resume { sntask.resume }
	
	free {
		// 
		// stop tasks
		sntask.stop.reset;
		udtask.stop.reset;
		"lastDur is ".post; lastDur.postln;
		SystemClock.sched( lastDur + 3, { group.deepFree; srcBus.free; routeBus.free; voiceBus.free; sntask=nil; udtask=nil; "wind freed".postln} );
		// free source bus after 10 sec
	}
	
	killNow {
		// 
		// stop tasks
		sntask.stop.reset;
		udtask.stop.reset;
		SystemClock.sched( 0.1, { group.deepFree; srcBus.free; routeBus.free; voiceBus.free; sntask=udtask=nil; "wind killed".postln} );
		// free source bus after 10 sec
	}
		// to be used by a higher-level control-structure with state presets
	updateState { | transitionTime, grnRate, lowerPartial, upperPartial, taskwait = 6, curve=0 |
		var currate;
			// in case the task is already playing
		udtask.stop.reset;
		
		currate = srcsynth.grnrate;
		//"current rate: ".post; currate.postln;
		rateEnv = Env( [ currate, grnRate],[transitionTime], curve );
		waitEnv = Env( [twait, taskwait],[transitionTime], curve );
		upEnv = Env( [upar,upperPartial],[transitionTime], curve );
		lpEnv = Env( [lpar,lowerPartial],[transitionTime], curve );
				
		udtask.play;
		SystemClock.sched( transitionTime+1, { udtask.stop.reset; "updating is complete".postln } );
	}
	
	grainrate { ^srcsynth.grnrate }
	
	grainrate_{ | rate | srcsynth.grnrate_( rate ) }
	
	amp_{ | argamp | srcsynth.grainAmp_(argamp) }
	
	lowerPartial_{ | lp | lpar = lp }
	
	upperPartial_{ | up | upar = up }
	
	gustWeights_{ | weightArray | gustWeights = weightArray }
	
	wait_{ | wait | twait = wait }
	
	sendToVoice { |fadein=8| routesynth.fade_(fadein).voicemix_(1) }
	
	removeVoice { |fadeout=8| routesynth.fade_(fadeout).voicemix_(0) }
	
}